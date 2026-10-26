package com.ktb10.kgb.common.config;

import com.ktb10.kgb.member.session.RedisServiceSessionAuthenticationStore;
import com.ktb10.kgb.member.session.RedisServiceSessionLifecycleStore;
import com.ktb10.kgb.member.session.ServiceSessionAuthenticationStore;
import com.ktb10.kgb.member.session.ServiceSessionLifecycleStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** OAuth 임시 HttpSession과 별도 namespace로 서비스 세션을 관리합니다. */
@Configuration
public class ServiceSessionRedisConfig {

    @Bean
    public ServiceSessionAuthenticationStore serviceSessionAuthenticationStore(
            StringRedisTemplate redis, @Value("${auth.session.redis.namespace}") String namespace) {
        return new RedisServiceSessionAuthenticationStore(redis, namespace);
    }

    @Bean
    public ServiceSessionLifecycleStore serviceSessionLifecycleStore(
            StringRedisTemplate redis, @Value("${auth.session.redis.namespace}") String namespace) {
        return new RedisServiceSessionLifecycleStore(redis, namespace);
    }
}
