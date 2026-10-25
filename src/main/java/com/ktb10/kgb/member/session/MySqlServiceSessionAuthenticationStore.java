package com.ktb10.kgb.member.session;

import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 기존 MySQL 행 잠금과 트랜잭션 안에서 세션을 검증하고 활동을 갱신합니다. */
@Component
public class MySqlServiceSessionAuthenticationStore implements ServiceSessionAuthenticationStore {

    private final AuthSessionRepository authSessionRepository;
    private final Clock clock;

    public MySqlServiceSessionAuthenticationStore(AuthSessionRepository authSessionRepository, Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<AuthenticatedMember> authenticateAndRecordUse(byte[] sessionIdHash, Duration idleTimeout) {
        Optional<AuthSession> foundSession = authSessionRepository
                .findBySessionIdHashForAuthentication(sessionIdHash);
        if (foundSession.isEmpty()) {
            return Optional.empty();
        }

        AuthSession session = foundSession.get();
        // 기존 동작처럼 잠금 획득 후의 시각으로 유효성을 검사합니다.
        LocalDateTime now = LocalDateTime.now(clock);
        Member member = session.getMember();
        if (!session.isUsable(now, idleTimeout) || member.isDeleted()) {
            return Optional.empty();
        }

        session.recordUse(now);
        return Optional.of(new AuthenticatedMember(member.getId(), member.getStatus(), session.getId()));
    }
}
