package com.ktb10.kgb.member.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 서비스 세션과 회원 상태를 검증하고 요청 인증 정보를 만듭니다. */
@Service
public class ServiceSessionService {

    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    private final AuthSessionRepository authSessionRepository;
    private final SessionIdHasher sessionIdHasher;
    private final Clock clock;

    public ServiceSessionService(
            AuthSessionRepository authSessionRepository,
            SessionIdHasher sessionIdHasher,
            Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.sessionIdHasher = sessionIdHasher;
        this.clock = clock;
    }

    @Transactional
    public Optional<AuthenticatedMember> authenticate(String rawSessionId) {
        byte[] sessionIdHash = sessionIdHasher.hash(rawSessionId);
        Optional<AuthSession> foundSession = authSessionRepository
                .findBySessionIdHashForAuthentication(sessionIdHash);
        if (foundSession.isEmpty()) {
            return Optional.empty();
        }

        AuthSession session = foundSession.get();
        LocalDateTime now = LocalDateTime.now(clock);
        Member member = session.getMember();
        if (!session.isUsable(now, IDLE_TIMEOUT) || member.isDeleted()) {
            return Optional.empty();
        }

        session.recordUse(now);
        return Optional.of(new AuthenticatedMember(
                member.getId(),
                member.getStatus(),
                session.getId()));
    }

    @Transactional
    public void revokeCurrent(Long memberId, Long sessionId) {
        AuthSession session = authSessionRepository
                .findByIdAndMemberIdAndRevokedAtIsNull(sessionId, memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.AUTH_SESSION_REQUIRED));
        session.revoke(LocalDateTime.now(clock));
    }
}
