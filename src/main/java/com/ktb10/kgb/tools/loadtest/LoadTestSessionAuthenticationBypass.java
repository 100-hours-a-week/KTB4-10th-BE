package com.ktb10.kgb.tools.loadtest;

import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.member.entity.MemberStatus;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 로컬 부하 테스트 fixture 세션을 DB 접근 없이 인증합니다. */
@Component
@Profile("local & loadtest")
@ConditionalOnProperty(
        name = "load-test.authentication-bypass-enabled",
        havingValue = "true")
public class LoadTestSessionAuthenticationBypass {

    private final Map<String, AuthenticatedMember> sessions = new ConcurrentHashMap<>();

    public Optional<AuthenticatedMember> authenticate(String rawSessionId) {
        return Optional.ofNullable(sessions.get(rawSessionId));
    }

    public void replace(String rawSessionId, Long memberId, Long sessionId) {
        sessions.put(
                rawSessionId,
                new AuthenticatedMember(memberId, MemberStatus.ACTIVE, sessionId));
    }

    public void clear() {
        sessions.clear();
    }
}
