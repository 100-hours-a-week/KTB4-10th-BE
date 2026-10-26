package com.ktb10.kgb.member.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.member.event.NotificationCreatedEvent;
import com.ktb10.kgb.member.event.NotificationRealtimeEnvelope;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 알림과 연결 종료 이벤트를 공용 Redis 채널에 best-effort로 발행합니다. */
@Component
@ConditionalOnProperty(name = "notification.redis.enabled", havingValue = "true")
public class RedisNotificationRealtimePublisher implements NotificationRealtimePublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            RedisNotificationRealtimePublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationRealtimeMetrics metrics;
    private final Clock clock;
    private final String channel;

    public RedisNotificationRealtimePublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            NotificationRealtimeMetrics metrics,
            Clock clock,
            @Value("${notification.redis.channel}") String channel) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.clock = clock;
        this.channel = requireChannel(channel);
    }

    @Override
    public void publishNotification(NotificationCreatedEvent event) {
        publish(NotificationRealtimeEnvelope.notification(event, Instant.now(clock)));
    }

    @Override
    public void publishSessionDisconnected(Long memberId, String sessionHash) {
        publish(NotificationRealtimeEnvelope.sessionDisconnected(
                memberId,
                sessionHash,
                Instant.now(clock)));
    }

    @Override
    public void publishMemberDisconnected(Long memberId) {
        publish(NotificationRealtimeEnvelope.memberDisconnected(memberId, Instant.now(clock)));
    }

    private void publish(NotificationRealtimeEnvelope envelope) {
        try {
            redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(envelope));
            metrics.recordRedisPublish(envelope.eventType(), "success");
        } catch (JsonProcessingException | RuntimeException exception) {
            metrics.recordRedisPublish(envelope.eventType(), "failure");
            LOGGER.warn(
                    "Redis 실시간 알림 이벤트 발행에 실패했습니다. eventId={}, eventType={}",
                    envelope.eventId(),
                    envelope.eventType(),
                    exception);
        }
    }

    private String requireChannel(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Redis 알림 채널은 비어 있을 수 없습니다.");
        }
        return value;
    }
}
