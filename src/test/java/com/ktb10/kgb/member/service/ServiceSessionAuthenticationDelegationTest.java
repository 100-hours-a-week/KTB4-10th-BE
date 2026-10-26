package com.ktb10.kgb.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.session.ServiceSessionLifecycleStore;
import com.ktb10.kgb.member.repository.WebPushSubscriptionRepository;
import com.ktb10.kgb.member.session.ServiceSessionAuthenticationStore;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceSessionAuthenticationDelegationTest {

    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    @Mock
    private ServiceSessionAuthenticationStore authenticationStore;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ServiceSessionLifecycleStore lifecycleStore;

    @Mock
    private WebPushSubscriptionRepository webPushSubscriptionRepository;

    private final SessionIdHasher sessionIdHasher = new SessionIdHasher();
    private ServiceSessionService service;

    @BeforeEach
    void setUp() {
        service = new ServiceSessionService(
                authenticationStore,
                lifecycleStore,
                memberRepository,
                webPushSubscriptionRepository,
                sessionIdHasher,
                Clock.systemUTC(),
                IDLE_TIMEOUT);
    }

    @Test
    void delegatesHashedCookieAndLoadsCurrentMemberFromMysql() {
        byte[] hash = sessionIdHasher.hash("opaque-session-cookie");
        AuthenticatedMember principal = new AuthenticatedMember(7L, MemberStatus.ACTIVE, java.util.HexFormat.of().formatHex(hash));
        when(authenticationStore.authenticateAndRecordUse(hash, IDLE_TIMEOUT))
                .thenReturn(Optional.of(7L));
        Member member = org.mockito.Mockito.mock(Member.class);
        when(member.getId()).thenReturn(7L);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(memberRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(member));

        assertThat(service.authenticate("opaque-session-cookie")).contains(principal);

        verify(authenticationStore).authenticateAndRecordUse(hash, IDLE_TIMEOUT);
        verifyNoMoreInteractions(authenticationStore);
        verify(memberRepository).findByIdAndDeletedAtIsNull(7L);
        verifyNoInteractions(lifecycleStore, webPushSubscriptionRepository);
    }

    @Test
    void doesNotFallBackToMysqlWhenStoreRejectsSession() {
        byte[] hash = sessionIdHasher.hash("unknown-session");
        when(authenticationStore.authenticateAndRecordUse(hash, IDLE_TIMEOUT))
                .thenReturn(Optional.empty());

        assertThat(service.authenticate("unknown-session")).isEmpty();

        verifyNoInteractions(memberRepository, webPushSubscriptionRepository);
    }

    @Test
    void propagatesStoreFailureInsteadOfTreatingItAsMissingSession() {
        byte[] hash = sessionIdHasher.hash("session-with-store-failure");
        IllegalStateException failure = new IllegalStateException("Session store unavailable");
        when(authenticationStore.authenticateAndRecordUse(hash, IDLE_TIMEOUT)).thenThrow(failure);

        assertThatThrownBy(() -> service.authenticate("session-with-store-failure"))
                .isSameAs(failure);

        verifyNoInteractions(memberRepository, webPushSubscriptionRepository);
    }
}
