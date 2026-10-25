package com.ktb10.kgb.member.session;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb10.kgb.member.entity.MemberStatus;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisServiceSessionLifecycleStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RedisServiceSessionLifecycleStore store = new RedisServiceSessionLifecycleStore(redis, "test");

    @Test
    void rejectsInvalidArgumentsBeforeRedisAccess() {
        assertThatThrownBy(() -> issue(new byte[31], 1L, 1L, MemberStatus.ACTIVE, Duration.ofHours(8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issue(new byte[32], 0L, 1L, MemberStatus.ACTIVE, Duration.ofHours(8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issue(new byte[32], 1L, -1L, MemberStatus.ACTIVE, Duration.ofHours(8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issue(new byte[32], 1L, 1L, null, Duration.ofHours(8)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.revokeCurrent(null, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.revokeAllForMember(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisServiceSessionLifecycleStore(redis, " "))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(redis);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1S", "PT0.000001S", "PT0.001001S", "PT9007199254740.992S"})
    void rejectsNonPositiveFractionalOrUnsafeDurations(String input) {
        Duration duration = Duration.parse(input);
        assertThatThrownBy(() -> issue(new byte[32], 1L, 1L, MemberStatus.ACTIVE, duration))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.issueReplacingForMember(new byte[32], 1L, 1L,
                MemberStatus.ACTIVE, Duration.ofHours(8), duration)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(redis);
    }

    @Test
    void redisFailuresPropagateForEveryOperation() {
        var failure = new RedisConnectionFailureException("test unavailable");
        doThrow(failure).when(redis).execute(
                ArgumentMatchers.<RedisScript<String>>any(), anyList(), any(Object[].class));
        assertThatThrownBy(() -> issue(new byte[32], 1L, 1L, MemberStatus.ACTIVE, Duration.ofHours(8)))
                .isSameAs(failure);
        assertThatThrownBy(() -> store.revokeCurrent(new byte[32], 1L, 1L)).isSameAs(failure);
        assertThatThrownBy(() -> store.revokeAllForMember(1L)).isSameAs(failure);
    }

    private void issue(byte[] hash, Long sessionId, Long memberId, MemberStatus status, Duration lifetime) {
        store.issueReplacingForMember(hash, sessionId, memberId, status, lifetime, Duration.ofMinutes(30));
    }
}
