package com.ktb10.kgb.member.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.WebPushSubscriptionStatus;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.WebPushSubscriptionRepository;
import com.ktb10.kgb.member.session.ServiceSessionAuthenticationStore;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 서비스 세션과 회원 상태를 검증하고 요청 인증 정보를 만듭니다. */
@Service
public class ServiceSessionService {

    private final ServiceSessionAuthenticationStore authenticationStore;
    private final AuthSessionRepository authSessionRepository;
    private final WebPushSubscriptionRepository webPushSubscriptionRepository;
    private final SessionIdHasher sessionIdHasher;
    private final Clock clock;
    private final Duration idleTimeout;

    public ServiceSessionService(
            ServiceSessionAuthenticationStore authenticationStore,
            AuthSessionRepository authSessionRepository,
            WebPushSubscriptionRepository webPushSubscriptionRepository,
            SessionIdHasher sessionIdHasher,
            Clock clock,
            @Value("${auth.session.idle-timeout:30m}") Duration idleTimeout) {
        this.authenticationStore = authenticationStore;
        this.authSessionRepository = authSessionRepository;
        this.webPushSubscriptionRepository = webPushSubscriptionRepository;
        this.sessionIdHasher = sessionIdHasher;
        this.clock = clock;
        this.idleTimeout = idleTimeout;
    }

    public Optional<AuthenticatedMember> authenticate(String rawSessionId) {
        byte[] sessionIdHash = sessionIdHasher.hash(rawSessionId);
        return authenticationStore.authenticateAndRecordUse(sessionIdHash, idleTimeout);
    }

    @Transactional
    public void revokeCurrent(Long memberId, Long sessionId) {
        AuthSession session = authSessionRepository
                .findByIdAndMemberIdAndRevokedAtIsNull(sessionId, memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.AUTH_SESSION_REQUIRED));
        LocalDateTime now = LocalDateTime.now(clock);
        session.revoke(now);
        webPushSubscriptionRepository.revokeAllActiveByAuthSessionId(
                sessionId,
                WebPushSubscriptionStatus.ACTIVE,
                WebPushSubscriptionStatus.REVOKED,
                now);
    }
}
