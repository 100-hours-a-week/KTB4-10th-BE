package com.ktb10.kgb.member.service;

import com.ktb10.kgb.member.event.NotificationCreatedEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
    private final ConcurrentMap<Long, ConcurrentMap<String, SseEmitter>> memberConnections =
            new ConcurrentHashMap<>();

    public NotificationStreamService(
            NotificationSseEmitterFactory emitterFactory,
            @Value("${notification.sse.connection-timeout:25m}") Duration connectionTimeout,
            @Value("${notification.sse.reconnect-time-millis:3000}") long reconnectTimeMillis) {
        if (connectionTimeout.isZero() || connectionTimeout.isNegative()) {
            throw new IllegalArgumentException("SSE 연결 제한 시간은 0보다 커야 합니다.");
        }
        if (reconnectTimeMillis < 0) {
            throw new IllegalArgumentException("SSE 재연결 시간은 음수일 수 없습니다.");
        }
        this.emitterFactory = emitterFactory;
        this.connectionTimeout = connectionTimeout;
        this.reconnectTimeMillis = reconnectTimeMillis;
    }

    public SseEmitter subscribe(Long memberId) {
        Objects.requireNonNull(memberId, "회원 ID는 null일 수 없습니다.");
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = emitterFactory.create(connectionTimeout.toMillis());
        memberConnections
                .computeIfAbsent(memberId, ignored -> new ConcurrentHashMap<>())
                .put(connectionId, emitter);
        registerCleanupCallbacks(memberId, connectionId, emitter);

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

    public void publish(NotificationCreatedEvent event) {
        if (!event.realtimeDeliveryEnabled()) {
            return;
        }
        ConcurrentMap<String, SseEmitter> connections =
                memberConnections.get(event.recipientMemberId());
        if (connections == null) {
            return;
        }

        connections.forEach((connectionId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .id(event.notification().notificationId())
                        .name(NOTIFICATION_EVENT_NAME)
                        .reconnectTime(reconnectTimeMillis)
                        .data(event.notification()));
            } catch (IOException | IllegalStateException exception) {
                removeConnection(event.recipientMemberId(), connectionId, emitter);
                emitter.completeWithError(exception);
                LOGGER.debug(
                        "SSE 알림 전송 실패로 연결을 제거했습니다. memberId={}, connectionId={}",
                        event.recipientMemberId(),
                        connectionId);
            }
        });
    }

    public void disconnectMember(Long memberId) {
        ConcurrentMap<String, SseEmitter> connections = memberConnections.remove(memberId);
        if (connections != null) {
            connections.values().forEach(SseEmitter::complete);
        }
    }

    @Scheduled(fixedDelayString = "${notification.sse.heartbeat-interval-millis:15000}")
    public void sendHeartbeat() {
        memberConnections.forEach((memberId, connections) ->
                connections.forEach((connectionId, emitter) -> {
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
        ConcurrentMap<String, SseEmitter> connections = memberConnections.get(memberId);
        if (connections == null) {
            return;
        }
        connections.remove(connectionId, emitter);
        if (connections.isEmpty()) {
            memberConnections.remove(memberId, connections);
        }
    }

    int connectionCount(Long memberId) {
        ConcurrentMap<String, SseEmitter> connections = memberConnections.get(memberId);
        return connections == null ? 0 : connections.size();
    }
}
