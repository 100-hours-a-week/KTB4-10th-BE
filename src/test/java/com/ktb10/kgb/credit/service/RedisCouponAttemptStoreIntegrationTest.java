package com.ktb10.kgb.credit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.credit.service.CouponAttemptStore.FailureRecordResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RedisCouponAttemptStoreIntegrationTest {

    private static final String NAMESPACE = "kgb:test:coupon:attempt";
    private static final Long MEMBER_ID = 7L;
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(10);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(10);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory firstConnectionFactory;
    private static LettuceConnectionFactory secondConnectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisCouponAttemptStore firstStore;
    private static RedisCouponAttemptStore secondStore;

    @BeforeAll
    static void setUpStores() {
        firstConnectionFactory = connectionFactory();
        secondConnectionFactory = connectionFactory();
        redisTemplate = new StringRedisTemplate(firstConnectionFactory);
        firstStore = new RedisCouponAttemptStore(
                redisTemplate,
                NAMESPACE,
                WINDOW_DURATION,
                BLOCK_DURATION,
                5);
        secondStore = new RedisCouponAttemptStore(
                new StringRedisTemplate(secondConnectionFactory),
                NAMESPACE,
                WINDOW_DURATION,
                BLOCK_DURATION,
                5);
    }

    @AfterAll
    static void closeConnections() {
        firstConnectionFactory.destroy();
        secondConnectionFactory.destroy();
    }

    @AfterEach
    void clearKeys() {
        redisTemplate.delete(redisTemplate.keys(NAMESPACE + ":*"));
    }

    @Test
    void sharesFifthFailureBlockAcrossInstances() {
        for (int attempt = 0; attempt < 4; attempt++) {
            assertThat(firstStore.recordFailure(MEMBER_ID).blocked()).isFalse();
        }

        FailureRecordResult fifthFailure = secondStore.recordFailure(MEMBER_ID);

        assertThat(fifthFailure.blocked()).isTrue();
        assertThat(fifthFailure.activated()).isTrue();
        assertThat(fifthFailure.retryAfterMillis()).isEqualTo(BLOCK_DURATION.toMillis());
        assertThat(firstStore.remainingBlockMillis(MEMBER_ID))
                .isBetween(BLOCK_DURATION.minusSeconds(5).toMillis(), BLOCK_DURATION.toMillis());
    }

    @Test
    void activatesBlockOnlyOnceForConcurrentFailures() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Callable<FailureRecordResult>> tasks = new ArrayList<>();
            for (int attempt = 0; attempt < 10; attempt++) {
                RedisCouponAttemptStore store = attempt % 2 == 0 ? firstStore : secondStore;
                tasks.add(() -> store.recordFailure(MEMBER_ID));
            }

            List<Future<FailureRecordResult>> futures = executor.invokeAll(tasks);
            List<FailureRecordResult> results = new ArrayList<>();
            for (Future<FailureRecordResult> future : futures) {
                results.add(future.get());
            }

            assertThat(results).filteredOn(FailureRecordResult::activated).hasSize(1);
            assertThat(results).filteredOn(FailureRecordResult::blocked).hasSize(6);
            assertThat(firstStore.remainingBlockMillis(MEMBER_ID)).isPositive();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void resetRemovesFailureAndBlockKeys() {
        for (int attempt = 0; attempt < 5; attempt++) {
            firstStore.recordFailure(MEMBER_ID);
        }

        secondStore.reset(MEMBER_ID);

        assertThat(secondStore.remainingBlockMillis(MEMBER_ID)).isZero();
        assertThat(redisTemplate.keys(NAMESPACE + ":*")).isEmpty();
    }

    private static LettuceConnectionFactory connectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(),
                REDIS.getFirstMappedPort());
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        return connectionFactory;
    }
}
