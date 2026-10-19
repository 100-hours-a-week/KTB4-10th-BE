package com.ktb10.kgb.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
}
