package com.ktb10.kgb.member.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb10.kgb.member.entity.MemberStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisServiceSessionAuthenticationStoreIntegrationTest {
    private static final byte[] HASH = new byte[32];
    private static final Duration IDLE = Duration.ofMinutes(30);
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);
    private static LettuceConnectionFactory firstFactory;
    private static LettuceConnectionFactory secondFactory;
    private static StringRedisTemplate redis;
    private static StringRedisTemplate secondRedis;
    private RedisServiceSessionAuthenticationStore store;
    private RedisServiceSessionAuthenticationStore secondStore;
    private String key;
    private long now;

    @BeforeAll
    static void connect() {
        firstFactory = factory();
        secondFactory = factory();
        redis = new StringRedisTemplate(firstFactory);
        secondRedis = new StringRedisTemplate(secondFactory);
    }

    private static LettuceConnectionFactory factory() {
        var factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getFirstMappedPort()));
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    @AfterAll
    static void disconnect() {
        if (firstFactory != null) firstFactory.destroy();
        if (secondFactory != null) secondFactory.destroy();
    }

    @BeforeEach
    void prepare() {
        String namespace = "test:session:" + UUID.randomUUID();
        store = new RedisServiceSessionAuthenticationStore(redis, namespace);
        secondStore = new RedisServiceSessionAuthenticationStore(secondRedis, namespace);
        key = namespace + ":session:" + "00".repeat(32);
        now = redis.execute(new DefaultRedisScript<>(
                "local t=redis.call('TIME'); return tonumber(t[1])*1000+math.floor(tonumber(t[2])/1000)",
                Long.class), List.of());
    }

    private Map<String, String> valid() {
        return new HashMap<>(Map.of("schemaVersion", "2", "memberId", "9223372036854775806",
                "createdAtEpochMillis", Long.toString(now - 10000),
                "expiresAtEpochMillis", Long.toString(now + 3600000),
                "lastUsedAtEpochMillis", Long.toString(now - 5000)));
    }

    private void seed(Map<String, String> fields) {
        redis.opsForHash().putAll(key, fields);
        redis.expire(key, Duration.ofHours(2));
    }

    @Test
    void returnsMemberIdWithoutRoundingAndUpdatesOnlyActivityAndIdleTtl() {
        var fields = valid();
        seed(fields);
        assertThat(store.authenticateAndRecordUse(HASH, IDLE)).contains(Long.MAX_VALUE - 1);
        assertThat(Long.parseLong((String) redis.opsForHash().get(key, "lastUsedAtEpochMillis")))
                .isGreaterThanOrEqualTo(now);
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(1795000L, 1800000L);
        assertThat(redis.opsForHash().get(key, "expiresAtEpochMillis")).isEqualTo(fields.get("expiresAtEpochMillis"));
        assertThat(redis.opsForHash().keys(key)).doesNotContain("memberStatus", "sessionId", "memberDeleted");
    }

    @Test
    void absoluteLimitAndMissingLastUseFallbackArePreserved() {
        var fields = valid();
        fields.remove("lastUsedAtEpochMillis");
        fields.put("expiresAtEpochMillis", Long.toString(now + 60000));
        seed(fields);
        assertThat(store.authenticateAndRecordUse(HASH, IDLE)).isPresent();
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(55000L, 60000L);
    }

    @ParameterizedTest
    @CsvSource({"expiresAtEpochMillis,0", "lastUsedAtEpochMillis,1800000"})
    void rejectsAbsoluteOrIdleBoundaryWithoutTouchingRecord(String field, long age) {
        var fields = valid();
        fields.put("createdAtEpochMillis", Long.toString(now - 3600000));
        fields.put(field, Long.toString(now - age));
        seed(fields);
        assertThat(store.authenticateAndRecordUse(HASH, IDLE)).isEmpty();
        assertThat(redis.opsForHash().entries(key)).containsAllEntriesOf(fields);
    }

    @ParameterizedTest
    @CsvSource({"schemaVersion,1", "memberId,9223372036854775808", "memberId,0",
            "createdAtEpochMillis,garbage"})
    void corruptDataFailsBeforeAnyMutation(String field, String value) {
        var fields = valid();
        fields.put(field, value);
        seed(fields);
        assertThatThrownBy(() -> store.authenticateAndRecordUse(HASH, IDLE)).isInstanceOf(RuntimeException.class);
        assertThat(redis.opsForHash().entries(key)).containsAllEntriesOf(fields);
    }

    @Test
    void missingAndDeletedKeysAreNeverRecreated() {
        assertThat(store.authenticateAndRecordUse(HASH, IDLE)).isEmpty();
        seed(valid());
        redis.delete(key);
        assertThat(secondStore.authenticateAndRecordUse(HASH, IDLE)).isEmpty();
        assertThat(redis.hasKey(key)).isFalse();
    }
}
