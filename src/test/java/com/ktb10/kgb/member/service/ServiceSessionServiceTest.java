package com.ktb10.kgb.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.member.entity.WebPushSubscriptionStatus;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.WebPushSubscriptionRepository;
import com.ktb10.kgb.member.session.ServiceSessionAuthenticationStore;
import com.ktb10.kgb.member.session.ServiceSessionLifecycleStore;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ServiceSessionServiceTest {
    private final ServiceSessionAuthenticationStore auth = mock(ServiceSessionAuthenticationStore.class);
    private final ServiceSessionLifecycleStore lifecycle = mock(ServiceSessionLifecycleStore.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final WebPushSubscriptionRepository subscriptions = mock(WebPushSubscriptionRepository.class);
    private final SessionIdHasher hasher = new SessionIdHasher();
    private final Clock clock = Clock.systemUTC();
    private final Duration idle = Duration.ofMinutes(30);
    private final ServiceSessionService service = new ServiceSessionService(
            auth, lifecycle, members, subscriptions, hasher, clock, idle);

    @Test
    void memberStatusAlwaysComesFromMysqlAndHashComesFromCookie() {
        byte[] hash = hasher.hash("cookie");
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(7L);
        when(member.getStatus()).thenReturn(MemberStatus.ONBOARDING, MemberStatus.ACTIVE);
        when(auth.authenticateAndRecordUse(hash, idle)).thenReturn(Optional.of(7L));
        when(members.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(member));
        var first = service.authenticate("cookie").orElseThrow();
        var second = service.authenticate("cookie").orElseThrow();
        assertThat(first.status()).isEqualTo(MemberStatus.ONBOARDING);
        assertThat(second.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(second.sessionHash()).isEqualTo(HexFormat.of().formatHex(hash));
        verify(members, times(2)).findByIdAndDeletedAtIsNull(7L);
        verifyNoInteractions(lifecycle, subscriptions);
    }

    @Test
    void missingSessionDoesNotQueryMysql() {
        when(auth.authenticateAndRecordUse(hasher.hash("missing"), idle)).thenReturn(Optional.empty());
        assertThat(service.authenticate("missing")).isEmpty();
        verifyNoInteractions(members, subscriptions, lifecycle);
    }

    @Test
    void deletedMemberIsRejectedWithoutCachingItsStatus() {
        when(auth.authenticateAndRecordUse(hasher.hash("deleted"), idle)).thenReturn(Optional.of(7L));
        when(members.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.empty());
        assertThat(service.authenticate("deleted")).isEmpty();
    }

    @Test
    void redisFailureDoesNotFallBackToMysql() {
        var failure = new IllegalStateException("Redis unavailable");
        when(auth.authenticateAndRecordUse(hasher.hash("cookie"), idle)).thenThrow(failure);
        assertThatThrownBy(() -> service.authenticate("cookie")).isSameAs(failure);
        verifyNoInteractions(members, subscriptions, lifecycle);
    }

    @Test
    void logoutLocksMemberAndRevokesOnlyTheMatchingSubscriptionAssociation() {
        String hash = HexFormat.of().formatHex(hasher.hash("cookie"));
        when(members.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(mock(Member.class)));
        service.revokeCurrent(7L, hash);
        var order = inOrder(members, lifecycle, subscriptions);
        order.verify(members).findActiveByIdForUpdate(7L);
        order.verify(lifecycle).revokeCurrent(hasher.hash("cookie"), 7L);
        order.verify(subscriptions).revokeAllActiveByMemberIdAndSessionHash(
                eq(7L), eq(hash), eq(WebPushSubscriptionStatus.ACTIVE),
                eq(WebPushSubscriptionStatus.REVOKED), any());
    }

    @Test
    void redisFailureAbortsLogoutBeforePushMutation() {
        String hash = HexFormat.of().formatHex(hasher.hash("cookie"));
        when(members.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(mock(Member.class)));
        when(lifecycle.revokeCurrent(hasher.hash("cookie"), 7L)).thenThrow(new IllegalStateException("Redis down"));
        assertThatThrownBy(() -> service.revokeCurrent(7L, hash)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(subscriptions);
    }
}
