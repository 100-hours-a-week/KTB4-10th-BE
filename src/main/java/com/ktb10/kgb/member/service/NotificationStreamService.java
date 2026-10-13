package com.ktb10.kgb.member.service;

import com.ktb10.kgb.member.event.NotificationCreatedEvent;
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
    private final AtomicLong connectionSequence = new AtomicLong();
    private final ConcurrentMap<Long, ConcurrentMap<String, StreamConnection>> memberConnections =
            new ConcurrentHashMap<>();

    public NotificationStreamService(
            NotificationSseEmitterFactory emitterFactory,
            @Value("${notification.sse.connection-timeout:25m}") Duration connectionTimeout,
            @Value("${notification.sse.reconnect-time-millis:3000}") long reconnectTimeMillis,
            @Value("${notification.sse.max-connections-per-member:5}")
                    int maxConnectionsPerMember) {
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
    }

    public SseEmitter subscribe(Long memberId, Long sessionId) {
        Objects.requireNonNull(memberId, "회원 ID는 null일 수 없습니다.");
        Objects.requireNonNull(sessionId, "세션 ID는 null일 수 없습니다.");
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = emitterFactory.create(connectionTimeout.toMillis());
        AtomicReference<SseEmitter> evictedEmitter = new AtomicReference<>();
        memberConnections.compute(memberId, (ignored, existingConnections) -> {
            ConcurrentMap<String, StreamConnection> connections = existingConnections == null
                    ? new ConcurrentHashMap<>()
                    : existingConnections;
            evictedEmitter.set(addConnection(
                    connections,
                    connectionId,
                    new StreamConnection(
                            sessionId,
                            emitter,
                            connectionSequence.incrementAndGet())));
            return connections;
        });
        registerCleanupCallbacks(memberId, connectionId, emitter);
        if (evictedEmitter.get() != null) {
            evictedEmitter.get().complete();
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(CONNECTED_EVENT_NAME)
                    .reconnectTime(reconnectTimeMillis)
                    .data(Map.of("connection_id", connectionId)));
        } catch (IOException | IllegalStateException exception) {
            removeConnection(memberId, connectionId, emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    /** 알림을 연결된 SSE 스트림에 전송하고, 하나 이상의 전송 호출이 성공했는지 반환합니다. */
    public boolean publish(NotificationCreatedEvent event) {
        if (!event.realtimeDeliveryEnabled()) {
            return false;
        }
        ConcurrentMap<String, StreamConnection> connections =
                memberConnections.get(event.recipientMemberId());
        if (connections == null) {
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
            } catch (IOException | IllegalStateException exception) {
                removeConnection(event.recipientMemberId(), connectionId, emitter);
                emitter.completeWithError(exception);
                LOGGER.debug(
                        "SSE 알림 전송 실패로 연결을 제거했습니다. memberId={}, connectionId={}",
                        event.recipientMemberId(),
                        connectionId);
            }
        });
        return delivered.get();
    }

    public void disconnectMember(Long memberId) {
        ConcurrentMap<String, StreamConnection> connections = memberConnections.remove(memberId);
        if (connections != null) {
            connections.values().stream()
                    .map(StreamConnection::emitter)
                    .forEach(SseEmitter::complete);
        }
    }

    public void disconnectSession(Long memberId, Long sessionId) {
        List<SseEmitter> disconnectedEmitters = new ArrayList<>();
        memberConnections.computeIfPresent(memberId, (ignored, connections) -> {
            connections.forEach((connectionId, connection) -> {
                if (connection.sessionId().equals(sessionId)
                        && connections.remove(connectionId, connection)) {
                    disconnectedEmitters.add(connection.emitter());
                }
            });
            return connections.isEmpty() ? null : connections;
        });
        disconnectedEmitters.forEach(SseEmitter::complete);
    }

    @Scheduled(fixedDelayString = "${notification.sse.heartbeat-interval-millis:15000}")
    public void sendHeartbeat() {
        memberConnections.forEach((memberId, connections) ->
                connections.forEach((connectionId, connection) -> {
                    SseEmitter emitter = connection.emitter();
                    try {
                        emitter.send(SseEmitter.event().comment(HEARTBEAT_COMMENT));
                    } catch (IOException | IllegalStateException exception) {
                        removeConnection(memberId, connectionId, emitter);
                        emitter.completeWithError(exception);
                    }
                }));
    }

    private void registerCleanupCallbacks(
            Long memberId,
            String connectionId,
            SseEmitter emitter) {
        emitter.onCompletion(() -> removeConnection(memberId, connectionId, emitter));
        emitter.onTimeout(() -> removeConnection(memberId, connectionId, emitter));
        emitter.onError(ignored -> removeConnection(memberId, connectionId, emitter));
    }

    private void removeConnection(
            Long memberId,
            String connectionId,
            SseEmitter emitter) {
        memberConnections.computeIfPresent(memberId, (ignored, connections) -> {
            connections.computeIfPresent(connectionId, (ignoredConnectionId, connection) ->
                    connection.emitter() == emitter ? null : connection);
            return connections.isEmpty() ? null : connections;
        });
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

    private SseEmitter addConnection(
            ConcurrentMap<String, StreamConnection> connections,
            String connectionId,
            StreamConnection connection) {
        StreamConnection evictedConnection = null;
        if (connections.size() >= maxConnectionsPerMember) {
            Map.Entry<String, StreamConnection> oldestEntry = connections.entrySet().stream()
                    .min(Comparator.comparingLong(entry -> entry.getValue().sequence()))
                    .orElseThrow();
            evictedConnection = connections.remove(oldestEntry.getKey());
        }
        connections.put(connectionId, connection);
        return evictedConnection == null ? null : evictedConnection.emitter();
    }

    private record StreamConnection(Long sessionId, SseEmitter emitter, long sequence) {
    }
}
