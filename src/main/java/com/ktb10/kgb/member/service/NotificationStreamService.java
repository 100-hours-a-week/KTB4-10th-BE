package com.ktb10.kgb.member.service;

import com.ktb10.kgb.member.event.NotificationCreatedEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 단일 인스턴스에서 회원별 SSE 연결과 실시간 알림 전송을 관리합니다. */
@Service
public class NotificationStreamService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            NotificationStreamService.class);
    private static final String CONNECTED_EVENT_NAME = "connected";
    private static final String NOTIFICATION_EVENT_NAME = "notification";
    private static final String HEARTBEAT_COMMENT = "heartbeat";

    private final NotificationSseEmitterFactory emitterFactory;
    private final Duration connectionTimeout;
    private final long reconnectTimeMillis;
    private final int maxConnectionsPerMember;
    private final NotificationRealtimeMetrics metrics;
    private final AtomicLong connectionSequence = new AtomicLong();
    private final ConcurrentMap<Long, ConcurrentMap<String, StreamConnection>> memberConnections =
            new ConcurrentHashMap<>();

    public NotificationStreamService(
            NotificationSseEmitterFactory emitterFactory,
            @Value("${notification.sse.connection-timeout:25m}") Duration connectionTimeout,
            @Value("${notification.sse.reconnect-time-millis:3000}") long reconnectTimeMillis,
            @Value("${notification.sse.max-connections-per-member:5}")
                    int maxConnectionsPerMember,
            NotificationRealtimeMetrics metrics,
            MeterRegistry meterRegistry) {
        if (connectionTimeout.isZero() || connectionTimeout.isNegative()) {
            throw new IllegalArgumentException("SSE 연결 제한 시간은 0보다 커야 합니다.");
        }
        if (reconnectTimeMillis < 0) {
            throw new IllegalArgumentException("SSE 재연결 시간은 음수일 수 없습니다.");
        }
        if (maxConnectionsPerMember <= 0) {
            throw new IllegalArgumentException("회원별 SSE 최대 연결 수는 0보다 커야 합니다.");
        }
        this.emitterFactory = emitterFactory;
        this.connectionTimeout = connectionTimeout;
        this.reconnectTimeMillis = reconnectTimeMillis;
        this.maxConnectionsPerMember = maxConnectionsPerMember;
        this.metrics = metrics;
        Gauge.builder(
                        "kgb.notification.sse.connections",
                        memberConnections,
                        connections -> connections.values().stream()
                                .mapToInt(Map::size)
                                .sum())
                .description("현재 인스턴스가 보유한 SSE 연결 수")
                .register(meterRegistry);
    }

    public SseEmitter subscribe(Long memberId, Long sessionId) {
        Objects.requireNonNull(memberId, "회원 ID는 null일 수 없습니다.");
        Objects.requireNonNull(sessionId, "세션 ID는 null일 수 없습니다.");
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = emitterFactory.create(connectionTimeout.toMillis());
        AtomicReference<EvictedConnection> evictedConnection = new AtomicReference<>();
        memberConnections.compute(memberId, (ignored, existingConnections) -> {
            ConcurrentMap<String, StreamConnection> connections = existingConnections == null
                    ? new ConcurrentHashMap<>()
                    : existingConnections;
            evictedConnection.set(addConnection(
                    connections,
                    connectionId,
                    new StreamConnection(
                            sessionId,
                            emitter,
                            connectionSequence.incrementAndGet(),
                            System.nanoTime())));
            return connections;
        });
        registerCleanupCallbacks(memberId, connectionId, emitter);
        metrics.recordSseConnectionOpened();
        LOGGER.debug(
                "SSE 연결을 등록했습니다. connectionId={}, activeConnections={}",
                connectionId,
                activeConnectionCount());
        if (evictedConnection.get() != null) {
            observeConnectionClosed(
                    evictedConnection.get().connectionId(),
                    evictedConnection.get().connection(),
                    SseConnectionCloseReason.EVICTED);
            evictedConnection.get().connection().emitter().complete();
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(CONNECTED_EVENT_NAME)
                    .reconnectTime(reconnectTimeMillis)
                    .data(Map.of("connection_id", connectionId)));
        } catch (IOException | IllegalStateException exception) {
            removeConnection(
                    memberId,
                    connectionId,
                    emitter,
                    SseConnectionCloseReason.INITIAL_SEND_FAILURE);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    /** 알림을 연결된 SSE 스트림에 전송하고, 하나 이상의 전송 호출이 성공했는지 반환합니다. */
    public boolean publish(NotificationCreatedEvent event) {
        if (!event.realtimeDeliveryEnabled()) {
            metrics.recordSseDelivery("disabled");
            return false;
        }
        ConcurrentMap<String, StreamConnection> connections =
                memberConnections.get(event.recipientMemberId());
        if (connections == null) {
            metrics.recordSseDelivery("no_connection");
            return false;
        }

        AtomicBoolean delivered = new AtomicBoolean();
        connections.forEach((connectionId, connection) -> {
            SseEmitter emitter = connection.emitter();
            try {
                emitter.send(SseEmitter.event()
                        .id(event.notification().notificationId())
                        .name(NOTIFICATION_EVENT_NAME)
                        .reconnectTime(reconnectTimeMillis)
                        .data(event.notification()));
                delivered.set(true);
                metrics.recordSseDelivery("success");
            } catch (IOException | IllegalStateException exception) {
                metrics.recordSseDelivery("failure");
                removeConnection(
                        event.recipientMemberId(),
                        connectionId,
                        emitter,
                        SseConnectionCloseReason.NOTIFICATION_SEND_FAILURE);
                emitter.completeWithError(exception);
            }
        });
        return delivered.get();
    }

    public void disconnectMember(Long memberId) {
        ConcurrentMap<String, StreamConnection> connections = memberConnections.remove(memberId);
        if (connections != null) {
            connections.forEach((connectionId, connection) -> {
                observeConnectionClosed(
                        connectionId,
                        connection,
                        SseConnectionCloseReason.MEMBER_DISCONNECT);
                connection.emitter().complete();
            });
        }
    }

    public void disconnectSession(Long memberId, Long sessionId) {
        List<RemovedConnection> disconnectedConnections = new ArrayList<>();
        memberConnections.computeIfPresent(memberId, (ignored, connections) -> {
            connections.forEach((connectionId, connection) -> {
                if (connection.sessionId().equals(sessionId)
                        && connections.remove(connectionId, connection)) {
                    disconnectedConnections.add(new RemovedConnection(
                            connectionId,
                            connection));
                }
            });
            return connections.isEmpty() ? null : connections;
        });
        disconnectedConnections.forEach(removedConnection -> {
            observeConnectionClosed(
                    removedConnection.connectionId(),
                    removedConnection.connection(),
                    SseConnectionCloseReason.SESSION_DISCONNECT);
            removedConnection.connection().emitter().complete();
        });
    }

    @Scheduled(fixedDelayString = "${notification.sse.heartbeat-interval-millis:15000}")
    public void sendHeartbeat() {
        memberConnections.forEach((memberId, connections) ->
                connections.forEach((connectionId, connection) -> {
                    SseEmitter emitter = connection.emitter();
                    try {
                        emitter.send(SseEmitter.event().comment(HEARTBEAT_COMMENT));
                    } catch (IOException | IllegalStateException exception) {
                        removeConnection(
                                memberId,
                                connectionId,
                                emitter,
                                SseConnectionCloseReason.HEARTBEAT_FAILURE);
                        emitter.completeWithError(exception);
                    }
                }));
    }

    @Scheduled(fixedDelayString =
            "${notification.sse.observability-log-interval-millis:300000}")
    public void logConnectionSummary() {
        LOGGER.info(
                "SSE 연결 현황입니다. activeConnections={}, activeMembers={}",
                activeConnectionCount(),
                activeMemberCount());
    }

    private void registerCleanupCallbacks(
            Long memberId,
            String connectionId,
            SseEmitter emitter) {
        emitter.onCompletion(() -> removeConnection(
                memberId,
                connectionId,
                emitter,
                SseConnectionCloseReason.COMPLETION));
        emitter.onTimeout(() -> removeConnection(
                memberId,
                connectionId,
                emitter,
                SseConnectionCloseReason.TIMEOUT));
        emitter.onError(ignored -> removeConnection(
                memberId,
                connectionId,
                emitter,
                SseConnectionCloseReason.ERROR));
    }

    private void removeConnection(
            Long memberId,
            String connectionId,
            SseEmitter emitter,
            SseConnectionCloseReason reason) {
        AtomicReference<StreamConnection> removedConnection = new AtomicReference<>();
        memberConnections.computeIfPresent(memberId, (ignored, connections) -> {
            connections.computeIfPresent(connectionId, (ignoredConnectionId, connection) -> {
                if (connection.emitter() == emitter) {
                    removedConnection.set(connection);
                    return null;
                }
                return connection;
            });
            return connections.isEmpty() ? null : connections;
        });
        if (removedConnection.get() != null) {
            observeConnectionClosed(connectionId, removedConnection.get(), reason);
        }
    }

    int connectionCount(Long memberId) {
        ConcurrentMap<String, StreamConnection> connections = memberConnections.get(memberId);
        return connections == null ? 0 : connections.size();
    }

    int connectionCount(Long memberId, Long sessionId) {
        ConcurrentMap<String, StreamConnection> connections = memberConnections.get(memberId);
        if (connections == null) {
            return 0;
        }
        return (int) connections.values().stream()
                .filter(connection -> connection.sessionId().equals(sessionId))
                .count();
    }

    int activeConnectionCount() {
        return memberConnections.values().stream()
                .mapToInt(Map::size)
                .sum();
    }

    int activeMemberCount() {
        return memberConnections.size();
    }

    private EvictedConnection addConnection(
            ConcurrentMap<String, StreamConnection> connections,
            String connectionId,
            StreamConnection connection) {
        Map.Entry<String, StreamConnection> oldestEntry = null;
        if (connections.size() >= maxConnectionsPerMember) {
            oldestEntry = connections.entrySet().stream()
                    .min(Comparator.comparingLong(entry -> entry.getValue().sequence()))
                    .orElseThrow();
            connections.remove(oldestEntry.getKey());
        }
        connections.put(connectionId, connection);
        return oldestEntry == null
                ? null
                : new EvictedConnection(oldestEntry.getKey(), oldestEntry.getValue());
    }

    private void observeConnectionClosed(
            String connectionId,
            StreamConnection connection,
            SseConnectionCloseReason reason) {
        long lifetimeNanos = System.nanoTime() - connection.openedAtNanos();
        metrics.recordSseConnectionClosed(reason, lifetimeNanos);
        LOGGER.debug(
                "SSE 연결을 제거했습니다. connectionId={}, reason={},"
                        + " lifetimeMillis={}, activeConnections={}",
                connectionId,
                reason,
                Duration.ofNanos(Math.max(0L, lifetimeNanos)).toMillis(),
                activeConnectionCount());
    }

    private record StreamConnection(
            Long sessionId,
            SseEmitter emitter,
            long sequence,
            long openedAtNanos) {
    }

    private record EvictedConnection(String connectionId, StreamConnection connection) {
    }

    private record RemovedConnection(String connectionId, StreamConnection connection) {
    }
}
