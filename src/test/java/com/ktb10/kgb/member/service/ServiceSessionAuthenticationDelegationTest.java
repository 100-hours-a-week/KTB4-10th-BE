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
import com.ktb10.kgb.member.repository.AuthSessionRepository;
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
    private AuthSessionRepository authSessionRepository;

    @Mock
    private WebPushSubscriptionRepository webPushSubscriptionRepository;

    private final SessionIdHasher sessionIdHasher = new SessionIdHasher();
    private ServiceSessionService service;

    @BeforeEach
    void setUp() {
        service = new ServiceSessionService(
                authenticationStore,
                authSessionRepository,
                webPushSubscriptionRepository,
                sessionIdHasher,
                Clock.systemUTC(),
                IDLE_TIMEOUT);
    }

    @Test
    void delegatesHashedCookieAndReturnsStorePrincipalWithoutJpaAccess() {
        byte[] hash = sessionIdHasher.hash("opaque-session-cookie");
        AuthenticatedMember principal = new AuthenticatedMember(7L, MemberStatus.ACTIVE, 123L);
        when(authenticationStore.authenticateAndRecordUse(hash, IDLE_TIMEOUT))
                .thenReturn(Optional.of(principal));

        assertThat(service.authenticate("opaque-session-cookie")).contains(principal);

        verify(authenticationStore).authenticateAndRecordUse(hash, IDLE_TIMEOUT);
        verifyNoMoreInteractions(authenticationStore);
        verifyNoInteractions(authSessionRepository, webPushSubscriptionRepository);
    }

    @Test
    void doesNotFallBackToMysqlWhenStoreRejectsSession() {
        byte[] hash = sessionIdHasher.hash("unknown-session");
        when(authenticationStore.authenticateAndRecordUse(hash, IDLE_TIMEOUT))
                .thenReturn(Optional.empty());

        assertThat(service.authenticate("unknown-session")).isEmpty();

        verifyNoInteractions(authSessionRepository, webPushSubscriptionRepository);
    }

    @Test
    void propagatesStoreFailureInsteadOfTreatingItAsMissingSession() {
        byte[] hash = sessionIdHasher.hash("session-with-store-failure");
        IllegalStateException failure = new IllegalStateException("Session store unavailable");
        when(authenticationStore.authenticateAndRecordUse(hash, IDLE_TIMEOUT)).thenThrow(failure);

        assertThatThrownBy(() -> service.authenticate("session-with-store-failure"))
                .isSameAs(failure);

        verifyNoInteractions(authSessionRepository, webPushSubscriptionRepository);
    }
}
