package com.ktb10.kgb.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;

/** Redis에 저장하는 HTTP 세션을 안전한 JSON 형식으로 직렬화합니다. */
@Configuration
public class RedisSessionConfig {

    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer(
            ObjectMapper objectMapper) {
        ObjectMapper redisObjectMapper = objectMapper.copy();
        redisObjectMapper.registerModules(SecurityJackson2Modules.getModules(
                RedisSessionConfig.class.getClassLoader()));
        return new GenericJackson2JsonRedisSerializer(redisObjectMapper);
    }

    @Bean
    public RedisTemplate<String, Object> oauthAuthorizationRequestRedisTemplate(
            RedisConnectionFactory connectionFactory,
            RedisSerializer<Object> springSessionDefaultRedisSerializer) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(RedisSerializer.string());
        redisTemplate.setValueSerializer(springSessionDefaultRedisSerializer);
        redisTemplate.setHashKeySerializer(RedisSerializer.string());
        redisTemplate.setHashValueSerializer(springSessionDefaultRedisSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
