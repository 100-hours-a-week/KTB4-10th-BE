package com.ktb10.kgb.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** 테스트 JVM 전용 Redis. 애플리케이션이나 사용자 Redis를 사용하지 않습니다. */
public abstract class RedisSessionTestSupport {
    protected static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @Autowired
    protected SessionFixtures sessions;

    @DynamicPropertySource
    static void serviceRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("auth.session.redis.namespace", () -> "kgb:test:runtime");
    }

    @BeforeEach
    void clearServiceSessions() {
        sessions.clear();
    }
}
