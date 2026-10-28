package com.ktb10.kgb.guidebook.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** 생성 상태 전용 채널이며, 알림 push_enabled와 분리합니다. */
@Configuration
public class GenerationRedisConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationRedisConfig.class);

    @Bean
    @ConditionalOnProperty(name = "ai.generation.redis.enabled", havingValue = "true")
    public RedisMessageListenerContainer generationRedisListener(
            RedisConnectionFactory factory,
            ObjectMapper json,
            GenerationStreamService streams,
            @Value("${ai.generation.redis.channel}") String channel) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) -> {
            try {
                streams.publish(json.readValue(
                        new String(message.getBody(), StandardCharsets.UTF_8),
                        GenerationUpdate.class));
            } catch (Exception exception) {
                LOGGER.warn("생성 상태 Redis 이벤트를 처리하지 못했습니다.", exception);
            }
        }, new ChannelTopic(channel));
        return container;
    }
}
