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
        return new HashMap<>(Map.of(
                "schemaVersion", "1", "sessionId", "9223372036854775807", "memberId", "9223372036854775806",
                "memberStatus", "ACTIVE", "createdAtEpochMillis", Long.toString(now - 10000),
                "expiresAtEpochMillis", Long.toString(now + 3600000),
                "lastUsedAtEpochMillis", Long.toString(now - 5000), "revoked", "0", "memberDeleted", "0"));
    }

    private void seed(Map<String, String> fields) {
        redis.opsForHash().putAll(key, fields);
        redis.expire(key, Duration.ofHours(2));
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE", "ONBOARDING"})
    void authenticatesWithoutRoundingIdsAndRenewsIdleTtl(String status) {
        var fields = valid();
        fields.put("memberStatus", status);
        seed(fields);
        var principal = store.authenticateAndRecordUse(HASH, IDLE).orElseThrow();
        assertThat(principal.sessionId()).isEqualTo(Long.MAX_VALUE);
        assertThat(principal.memberId()).isEqualTo(Long.MAX_VALUE - 1);
        assertThat(principal.status()).isEqualTo(MemberStatus.valueOf(status));
        assertThat(Long.parseLong((String) redis.opsForHash().get(key, "lastUsedAtEpochMillis")))
                .isGreaterThanOrEqualTo(now);
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(1795000L, 1800000L);
        assertThat(redis.opsForHash().get(key, "expiresAtEpochMillis"))
                .isEqualTo(fields.get("expiresAtEpochMillis"));
    }

    @Test
    void capsTtlAtAbsoluteExpiryAndFallsBackToCreation() {
        var fields = valid();
        fields.remove("lastUsedAtEpochMillis");
        fields.put("expiresAtEpochMillis", Long.toString(now + 60000));
        seed(fields);
        assertThat(store.authenticateAndRecordUse(HASH, IDLE)).isPresent();
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(55000L, 60000L);
    }

    @ParameterizedTest
    @CsvSource({"revoked,1", "memberDeleted,1", "expiresAtEpochMillis,expired",
            "lastUsedAtEpochMillis,idle"})
    void rejectsInvalidLifecycleWithoutTouch(String field, String value) {
        var fields = valid();
        if (value.equals("expired")) value = Long.toString(now);
        if (value.equals("idle")) {
            value = Long.toString(now - IDLE.toMillis());
            fields.put("createdAtEpochMillis", value);
        }
        fields.put(field, value);
        seed(fields);
        assertThat(store.authenticateAndRecordUse(HASH, IDLE)).isEmpty();
        assertThat(redis.opsForHash().get(key, "lastUsedAtEpochMillis"))
                .isEqualTo(fields.get("lastUsedAtEpochMillis"));
    }

    @ParameterizedTest
    @CsvSource({"schemaVersion,2", "memberStatus,DELETED", "sessionId,9223372036854775808",
            "memberId,0", "createdAtEpochMillis,garbage", "revoked,garbage"})
    void corruptRecordsFailBeforeMutation(String field, String value) {
        var fields = valid();
        fields.put(field, value);
        seed(fields);
        assertThatThrownBy(() -> store.authenticateAndRecordUse(HASH, IDLE)).isInstanceOf(RuntimeException.class);
        assertThat(redis.opsForHash().entries(key)).containsAllEntriesOf(fields);
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isGreaterThan(7000000L);
    }

    @Test
    void absentAndExpiredKeysAreNotRecreated() {
        assertThat(store.authenticateAndRecordUse(HASH, IDLE)).isEmpty();
        seed(valid());
        redis.expireAt(key, java.time.Instant.EPOCH);
        assertThat(store.authenticateAndRecordUse(HASH, IDLE)).isEmpty();
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test
    void concurrentClientsAndDeletionCannotResurrectSession() throws Exception {
        seed(valid());
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                var client = i % 2 == 0 ? store : secondStore;
                tasks.add(() -> client.authenticateAndRecordUse(HASH, IDLE).isPresent());
            }
            for (var result : executor.invokeAll(tasks)) assertThat(result.get()).isTrue();
            tasks.add(() -> redis.delete(key));
            for (var result : executor.invokeAll(tasks)) result.get();
        }
        assertThat(redis.hasKey(key)).isFalse();
        assertThat(secondStore.authenticateAndRecordUse(HASH, IDLE)).isEmpty();
    }
}
