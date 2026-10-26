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

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "ONBOARDING"})
    void issuesAuthenticatableSessionWithRedisTimeAndInitialTtls(String status) {
        var issued = store.issueReplacingForMember(hash(1), Long.MAX_VALUE, MEMBER,
                MemberStatus.valueOf(status), ABSOLUTE, IDLE);
        var session = redis.opsForHash().entries(key(1));
        assertThat(issued.expiresAt()).isEqualTo(issued.createdAt().plus(ABSOLUTE));
        assertThat(issued.replacedSessionId()).isEmpty();
        assertThat(session.get("createdAtEpochMillis")).isEqualTo("" + issued.createdAt().toEpochMilli());
        assertThat(session.get("lastUsedAtEpochMillis")).isEqualTo(session.get("createdAtEpochMillis"));
        assertThat(session.get("expiresAtEpochMillis")).isEqualTo("" + issued.expiresAt().toEpochMilli());
        assertThat(ttl(key(1))).isBetween(IDLE.minusSeconds(5).toMillis(), IDLE.toMillis());
        long pointerTtl = ttl(memberKey(MEMBER));
        assertThat(pointerTtl).isBetween(ABSOLUTE.minusSeconds(5).toMillis(), ABSOLUTE.toMillis());
        var principal = authentication.authenticateAndRecordUse(hash(1), IDLE).orElseThrow();
        assertThat(principal.memberId()).isEqualTo(MEMBER);
        assertThat(principal.sessionId()).isEqualTo(Long.MAX_VALUE);
        assertThat(principal.status()).isEqualTo(MemberStatus.valueOf(status));
        assertThat(ttl(memberKey(MEMBER))).isLessThanOrEqualTo(pointerTtl);
    }

    @Test
    void initialTtlCannotExceedAbsoluteLifetime() {
        store.issueReplacingForMember(hash(1), 1L, MEMBER, MemberStatus.ACTIVE, Duration.ofMinutes(1), IDLE);
        assertThat(ttl(key(1))).isBetween(55000L, 60000L);
        assertThat(ttl(memberKey(MEMBER))).isBetween(55000L, 60000L);
    }

    @Test
    void replacementDeletesOnlyPreviousLoginAndReturnsItsId() {
        issue(1, MEMBER);
        issue(2, 7L);
        var replacement = issue(3, MEMBER);
        assertThat(replacement.replacedSessionId()).contains(1L);
        assertThat(redis.hasKey(key(1))).isFalse();
        assertThat(authentication.authenticateAndRecordUse(hash(1), IDLE)).isEmpty();
        assertThat(authentication.authenticateAndRecordUse(hash(2), IDLE)).isPresent();
        assertThat(authentication.authenticateAndRecordUse(hash(3), IDLE)).isPresent();
        assertThat(redis.opsForValue().get(memberKey(MEMBER))).isEqualTo(hex(3) + ":3");
    }

    @Test
    void collisionsAndReuseDoNotDeleteEitherLogin() {
        issue(1, MEMBER);
        issue(2, 7L);
        assertThatThrownBy(() -> store.issueReplacingForMember(hash(2), 3L, MEMBER,
                MemberStatus.ACTIVE, ABSOLUTE, IDLE)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> issue(1, MEMBER)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> store.issueReplacingForMember(hash(3), 1L, MEMBER,
                MemberStatus.ACTIVE, ABSOLUTE, IDLE)).isInstanceOf(RuntimeException.class);
        assertThat(authentication.authenticateAndRecordUse(hash(1), IDLE)).isPresent();
        assertThat(authentication.authenticateAndRecordUse(hash(2), IDLE)).isPresent();
        assertThat(redis.hasKey(key(3))).isFalse();
        assertThat(redis.opsForValue().get(memberKey(MEMBER))).isEqualTo(hex(1) + ":1");
    }

    @Test
    void revokeChecksOwnershipAndDelayedLogoutCannotDeleteNewLogin() {
        issue(1, MEMBER);
        assertThat(store.revokeCurrent(hash(1), 1L, 7L)).isFalse();
        assertThat(store.revokeCurrent(hash(1), 2L, MEMBER)).isFalse();
        assertThat(authentication.authenticateAndRecordUse(hash(1), IDLE)).isPresent();
        issue(2, MEMBER);
        assertThat(store.revokeCurrent(hash(1), 1L, MEMBER)).isFalse();
        assertThat(authentication.authenticateAndRecordUse(hash(2), IDLE)).isPresent();
        assertThat(store.revokeCurrent(hash(2), 2L, MEMBER)).isTrue();
        assertThat(store.revokeCurrent(hash(2), 2L, MEMBER)).isFalse();
        assertThat(authentication.authenticateAndRecordUse(hash(2), IDLE)).isEmpty();
        assertThat(redis.hasKey(memberKey(MEMBER))).isFalse();
    }

    @Test
    void memberRevocationIsIsolatedAndIdempotent() {
        issue(1, MEMBER);
        issue(2, 7L);
        assertThat(store.revokeAllForMember(MEMBER)).contains(1L);
        assertThat(store.revokeAllForMember(MEMBER)).isEmpty();
        assertThat(authentication.authenticateAndRecordUse(hash(1), IDLE)).isEmpty();
        assertThat(authentication.authenticateAndRecordUse(hash(2), IDLE)).isPresent();
        assertThat(redis.hasKey(memberKey(MEMBER))).isFalse();
    }

    @Test
    void idleExpiredSessionLeavesOnlyBoundedMetadataAndCanBeReplacedOrExplicitlyRevoked() {
        issue(1, MEMBER);
        redis.expireAt(key(1), Instant.EPOCH);
        assertThat(authentication.authenticateAndRecordUse(hash(1), IDLE)).isEmpty();
        assertThat(redis.hasKey(memberKey(MEMBER))).isTrue();
        assertThat(issue(2, MEMBER).replacedSessionId()).contains(1L);
        redis.expireAt(key(2), Instant.EPOCH);
        assertThat(store.revokeCurrent(hash(2), 2L, MEMBER)).isTrue();
        assertThat(redis.hasKey(memberKey(MEMBER))).isFalse();
        issue(3, MEMBER);
        redis.expireAt(key(3), Instant.EPOCH);
        assertThat(store.revokeAllForMember(MEMBER)).contains(3L);
        assertThat(redis.hasKey(memberKey(MEMBER))).isFalse();
    }

    @Test
    void staleLogoutDoesNotRemoveAnotherMembersIndex() {
        issue(1, MEMBER);
        issue(2, 7L);
        assertThat(store.revokeCurrent(hash(1), 1L, 7L)).isFalse();
        assertThat(redis.opsForValue().get(memberKey(7L))).isEqualTo(hex(2) + ":2");
        assertThat(authentication.authenticateAndRecordUse(hash(1), IDLE)).isPresent();
    }

    @Test
    void logoutCanStillDeleteItsSessionWhenIndexIsMissing() {
        issue(1, MEMBER);
        redis.delete(memberKey(MEMBER));
        assertThat(store.revokeCurrent(hash(1), 1L, MEMBER)).isTrue();
        assertThat(authentication.authenticateAndRecordUse(hash(1), IDLE)).isEmpty();
    }

    @Test
    void removingOrphanedOldSessionLeavesCurrentIndexUntouched() {
        issue(1, MEMBER);
        var oldSession = redis.opsForHash().entries(key(1));
        issue(2, MEMBER);
        redis.opsForHash().putAll(key(1), oldSession);
        redis.expire(key(1), IDLE);
        assertThat(store.revokeCurrent(hash(1), 1L, MEMBER)).isTrue();
        assertThat(redis.hasKey(key(1))).isFalse();
        assertThat(redis.opsForValue().get(memberKey(MEMBER))).isEqualTo(hex(2) + ":2");
        assertThat(authentication.authenticateAndRecordUse(hash(2), IDLE)).isPresent();
    }

    @ParameterizedTest
    @CsvSource({"schemaVersion,2", "memberId,7", "sessionId,999"})
    void corruptPreviousIdentityFailsBeforeAnyReplacementOrMemberDeletion(String field, String value) {
        issue(1, MEMBER);
        redis.opsForHash().put(key(1), field, value);
        var before = redis.opsForHash().entries(key(1));
        assertThatThrownBy(() -> issue(2, MEMBER)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> store.revokeAllForMember(MEMBER)).isInstanceOf(RuntimeException.class);
        assertThat(redis.opsForHash().entries(key(1))).isEqualTo(before);
        assertThat(redis.hasKey(key(2))).isFalse();
        assertThat(redis.opsForValue().get(memberKey(MEMBER))).isEqualTo(hex(1) + ":1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"broken", "wrong-type", "wrong-old-type", "wrong-new-type"})
    void malformedIndexOrWrongTypesFailBeforeOldLoginIsDeleted(String failure) {
        issue(1, MEMBER);
        switch (failure) {
            case "broken" -> redis.opsForValue().set(memberKey(MEMBER), "broken");
            case "wrong-type" -> {
                redis.delete(memberKey(MEMBER));
                redis.opsForList().rightPush(memberKey(MEMBER), "broken");
            }
            case "wrong-old-type" -> {
                redis.delete(key(1));
                redis.opsForValue().set(key(1), "broken");
            }
            case "wrong-new-type" -> redis.opsForValue().set(key(2), "collision");
            default -> throw new IllegalArgumentException(failure);
        }
        assertThatThrownBy(() -> issue(2, MEMBER)).isInstanceOf(RuntimeException.class);
        assertThat(redis.hasKey(key(1))).isTrue();
        if (!failure.equals("wrong-new-type")) assertThat(redis.hasKey(key(2))).isFalse();
    }

    @Test
    void concurrentIssuanceAcrossClientsLeavesExactlyOneAuthenticatableSession() throws Exception {
        var gate = new CyclicBarrier(8);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<ServiceSessionLifecycleStore.IssueResult>> futures = new ArrayList<>();
            for (long id = 1; id <= 8; id++) {
                long sessionId = id;
                var client = id % 2 == 0 ? store : secondStore;
                futures.add(executor.submit(() -> {
                    gate.await(10, TimeUnit.SECONDS);
                    return client.issueReplacingForMember(hash(sessionId), sessionId, MEMBER,
                            MemberStatus.ACTIVE, ABSOLUTE, IDLE);
                }));
            }
            int replaced = 0;
            for (var future : futures) {
                if (future.get(10, TimeUnit.SECONDS).replacedSessionId().isPresent()) replaced++;
            }
            assertThat(replaced).isEqualTo(7);
        }
        int active = 0;
        for (long id = 1; id <= 8; id++) {
            if (authentication.authenticateAndRecordUse(hash(id), IDLE).isPresent()) {
                active++;
                assertThat(redis.opsForValue().get(memberKey(MEMBER))).isEqualTo(hex(id) + ":" + id);
            } else {
                assertThat(redis.hasKey(key(id))).isFalse();
            }
        }
        assertThat(active).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"current", "member"})
    void authenticationRacingRevocationNeverResurrectsSession(String mode) throws Exception {
        issue(1, MEMBER);
        var gate = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var auth = executor.submit(() -> {
                gate.await(10, TimeUnit.SECONDS);
                return authentication.authenticateAndRecordUse(hash(1), IDLE);
            });
            var revoke = executor.submit(() -> {
                gate.await(10, TimeUnit.SECONDS);
                return mode.equals("current") ? secondStore.revokeCurrent(hash(1), 1L, MEMBER)
                        : secondStore.revokeAllForMember(MEMBER).isPresent();
            });
            auth.get(10, TimeUnit.SECONDS);
            assertThat(revoke.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(redis.hasKey(key(1))).isFalse();
        assertThat(redis.hasKey(memberKey(MEMBER))).isFalse();
        assertThat(authentication.authenticateAndRecordUse(hash(1), IDLE)).isEmpty();
    }

    @Test
    void previousLogoutRacingNewLoginCannotRemoveNewSession() throws Exception {
        issue(1, MEMBER);
        var gate = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var login = executor.submit(() -> {
                gate.await(10, TimeUnit.SECONDS);
                return issue(2, MEMBER);
            });
            var logout = executor.submit(() -> {
                gate.await(10, TimeUnit.SECONDS);
                return secondStore.revokeCurrent(hash(1), 1L, MEMBER);
            });
            login.get(10, TimeUnit.SECONDS);
            logout.get(10, TimeUnit.SECONDS);
        }
        assertThat(authentication.authenticateAndRecordUse(hash(1), IDLE)).isEmpty();
        assertThat(authentication.authenticateAndRecordUse(hash(2), IDLE)).isPresent();
        assertThat(redis.opsForValue().get(memberKey(MEMBER))).isEqualTo(hex(2) + ":2");
    }

    private ServiceSessionLifecycleStore.IssueResult issue(long id, Long member) {
        return store.issueReplacingForMember(hash(id), id, member, MemberStatus.ACTIVE, ABSOLUTE, IDLE);
    }

    private static byte[] hash(long id) {
        return ByteBuffer.allocate(32).putLong(id).array();
    }

    private static String hex(long id) {
        return HexFormat.of().formatHex(hash(id));
    }

    private String key(long id) {
        return namespace + ":session:" + hex(id);
    }

    private String memberKey(Long id) {
        return namespace + ":member-session:" + id;
    }

    private long ttl(String key) {
        return redis.getExpire(key, TimeUnit.MILLISECONDS);
    }
}
