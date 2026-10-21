package com.ktb10.kgb.common.config;

import com.ktb10.kgb.member.service.RedisNotificationRealtimeSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** 다중 인스턴스 SSE fan-out을 위한 Redis Pub/Sub 구독 컨테이너를 구성합니다. */
@Configuration
@ConditionalOnProperty(name = "notification.redis.enabled", havingValue = "true")
public class NotificationRedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer notificationRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisNotificationRealtimeSubscriber subscriber,
            @Value("${notification.redis.channel}") String channel) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(requireChannel(channel)));
        return container;
    }

    private String requireChannel(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Redis 알림 채널은 비어 있을 수 없습니다.");
        }
        return value;
    }
}
