package com.ktb10.kgb.member.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb10.kgb.member.entity.MemberStatus;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisServiceSessionLifecycleStoreIntegrationTest {

    private static final Duration ABSOLUTE = Duration.ofHours(8);
    private static final Duration IDLE = Duration.ofMinutes(30);
    private static final Long MEMBER = Long.MAX_VALUE - 1;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory firstFactory;
    private static LettuceConnectionFactory secondFactory;
    private static StringRedisTemplate redis;
    private static StringRedisTemplate secondRedis;
    private String namespace;
    private RedisServiceSessionLifecycleStore store;
    private RedisServiceSessionLifecycleStore secondStore;
    private RedisServiceSessionAuthenticationStore authentication;

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
        namespace = "test:lifecycle:" + UUID.randomUUID();
        store = new RedisServiceSessionLifecycleStore(redis, namespace);
        secondStore = new RedisServiceSessionLifecycleStore(secondRedis, namespace);
        authentication = new RedisServiceSessionAuthenticationStore(redis, namespace);
    }

    @Test
    void issuedHashIsAuthenticatableWithoutCachingMemberStateOrNumericId() {
        var issued = issue(1, MEMBER);
        assertThat(issued.replacedSessionHash()).isEmpty();
        assertThat(issued.expiresAt()).isEqualTo(issued.createdAt().plus(ABSOLUTE));
        assertThat(authentication.authenticateAndRecordUse(hash(1), IDLE)).contains(MEMBER);
        assertThat(redis.opsForHash().keys(key(1))).doesNotContain("memberStatus", "sessionId", "memberDeleted");
        assertThat(redis.opsForValue().get(memberKey(MEMBER))).isEqualTo(hex(1));
        assertThat(ttl(key(1))).isBetween(IDLE.minusSeconds(5).toMillis(), IDLE.toMillis());
        assertThat(ttl(memberKey(MEMBER))).isBetween(ABSOLUTE.minusSeconds(5).toMillis(), ABSOLUTE.toMillis());
    }

    @Test
    void replacingSessionReturnsPreviousHashAndPreservesOtherMembers() {
        issue(1, MEMBER);
        issue(2, 7L);
        assertThat(issue(3, MEMBER).replacedSessionHash()).contains(hex(1));
        assertThat(authentication.authenticateAndRecordUse(hash(1), IDLE)).isEmpty();
        assertThat(authentication.authenticateAndRecordUse(hash(2), IDLE)).contains(7L);
        assertThat(authentication.authenticateAndRecordUse(hash(3), IDLE)).contains(MEMBER);
    }

    @Test
    void collisionDoesNotInvalidateExistingLogin() {
        issue(1, MEMBER);
        issue(2, 7L);
        assertThatThrownBy(() -> issue(1, MEMBER)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> issue(2, MEMBER)).isInstanceOf(RuntimeException.class);
        assertThat(authentication.authenticateAndRecordUse(hash(1), IDLE)).contains(MEMBER);
        assertThat(authentication.authenticateAndRecordUse(hash(2), IDLE)).contains(7L);
    }

    @Test
    void logoutIsOwnedIdempotentAndCannotDeleteTheNewLogin() {
        issue(1, MEMBER);
        assertThat(store.revokeCurrent(hash(1), 7L)).isFalse();
        assertThat(authentication.authenticateAndRecordUse(hash(1), IDLE)).contains(MEMBER);
        issue(2, MEMBER);
        assertThat(store.revokeCurrent(hash(1), MEMBER)).isFalse();
        assertThat(authentication.authenticateAndRecordUse(hash(2), IDLE)).contains(MEMBER);
        assertThat(store.revokeCurrent(hash(2), MEMBER)).isTrue();
        assertThat(store.revokeCurrent(hash(2), MEMBER)).isFalse();
        assertThat(redis.hasKey(memberKey(MEMBER))).isFalse();
    }

    @Test
    void memberRevocationAndIdleExpiryCleanOnlyTheirIndex() {
        issue(1, MEMBER);
        issue(2, 7L);
        redis.expireAt(key(1), Instant.EPOCH);
        assertThat(store.revokeAllForMember(MEMBER)).contains(hex(1));
        assertThat(store.revokeAllForMember(MEMBER)).isEmpty();
        assertThat(authentication.authenticateAndRecordUse(hash(2), IDLE)).contains(7L);
        assertThat(redis.hasKey(memberKey(MEMBER))).isFalse();
    }

    @Test
    void corruptedIndexAndOwnerFailBeforeReplacementOrDeletion() {
        issue(1, MEMBER);
        redis.opsForHash().put(key(1), "memberId", "7");
        assertThatThrownBy(() -> issue(2, MEMBER)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> store.revokeAllForMember(MEMBER)).isInstanceOf(RuntimeException.class);
        assertThat(redis.hasKey(key(1))).isTrue();
        assertThat(redis.hasKey(key(2))).isFalse();
    }

    @Test
    void concurrentClientsLeaveExactlyOneValidHash() throws Exception {
        var gate = new CyclicBarrier(8);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (long id = 1; id <= 8; id++) {
                long n = id;
                var client = id % 2 == 0 ? store : secondStore;
                futures.add(executor.submit(() -> {
                    gate.await(10, TimeUnit.SECONDS);
                    return client.issueReplacingForMember(hash(n), MEMBER, ABSOLUTE, IDLE);
                }));
            }
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        }
        int valid = 0;
        for (long id = 1; id <= 8; id++) {
            if (authentication.authenticateAndRecordUse(hash(id), IDLE).isPresent()) valid++;
        }
        assertThat(valid).isOne();
    }

    @Test
    void previousLogoutRacingLoginCannotRemoveNewSession() throws Exception {
        issue(1, MEMBER);
        var gate = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var login = executor.submit(() -> { gate.await(10, TimeUnit.SECONDS); return issue(2, MEMBER); });
            var logout = executor.submit(() -> {
                gate.await(10, TimeUnit.SECONDS);
                return secondStore.revokeCurrent(hash(1), MEMBER);
            });
            login.get(10, TimeUnit.SECONDS);
            logout.get(10, TimeUnit.SECONDS);
        }
        assertThat(authentication.authenticateAndRecordUse(hash(2), IDLE)).contains(MEMBER);
    }

    @Test
    void authenticationRacingDeletionDoesNotResurrectKey() throws Exception {
        issue(1, MEMBER);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var auth = executor.submit(() -> authentication.authenticateAndRecordUse(hash(1), IDLE));
            var revoke = executor.submit(() -> secondStore.revokeAllForMember(MEMBER));
            auth.get(10, TimeUnit.SECONDS);
            revoke.get(10, TimeUnit.SECONDS);
        }
        assertThat(redis.hasKey(key(1))).isFalse();
    }

    private ServiceSessionLifecycleStore.IssueResult issue(long id, Long member) {
        return store.issueReplacingForMember(hash(id), member, ABSOLUTE, IDLE);
    }
    private static byte[] hash(long id) { return ByteBuffer.allocate(32).putLong(id).array(); }
    private static String hex(long id) { return HexFormat.of().formatHex(hash(id)); }
    private String key(long id) { return namespace + ":session:" + hex(id); }
    private String memberKey(Long id) { return namespace + ":member-session:" + id; }
    private long ttl(String key) { return redis.getExpire(key, TimeUnit.MILLISECONDS); }
}
