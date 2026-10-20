package com.ktb10.kgb.member.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.member.event.NotificationRealtimeEnvelope;
import com.ktb10.kgb.member.event.NotificationRealtimeEventType;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/** Redis 이벤트를 역직렬화해 현재 인스턴스의 로컬 SSE adapter로 전달합니다. */
@Component
@ConditionalOnProperty(name = "notification.redis.enabled", havingValue = "true")
public class RedisNotificationRealtimeSubscriber implements MessageListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            RedisNotificationRealtimeSubscriber.class);

    private final ObjectMapper objectMapper;
    private final NotificationRealtimeAdapter adapter;
    private final NotificationRealtimeMetrics metrics;

    public RedisNotificationRealtimeSubscriber(
            ObjectMapper objectMapper,
            NotificationRealtimeAdapter adapter,
            NotificationRealtimeMetrics metrics) {
        this.objectMapper = objectMapper;
        this.adapter = adapter;
        this.metrics = metrics;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        NotificationRealtimeEventType eventType = null;
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            NotificationRealtimeEnvelope envelope = objectMapper.readValue(
                    json,
                    NotificationRealtimeEnvelope.class);
            eventType = envelope.eventType();
            adapter.handle(envelope);
            metrics.recordRedisSubscribe(eventType, "success");
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            metrics.recordRedisSubscribe(eventType, "invalid");
            LOGGER.warn("처리할 수 없는 Redis 실시간 알림 이벤트를 무시했습니다.", exception);
        } catch (RuntimeException exception) {
            metrics.recordRedisSubscribe(eventType, "failure");
            LOGGER.warn("Redis 실시간 알림 이벤트 처리에 실패했습니다.", exception);
        }
    }
}
