package com.ktb10.kgb.member.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** 원문 토큰 대신 SHA-256 해시로 세션 발급·교체·폐기를 처리합니다. */
public interface ServiceSessionLifecycleStore {

    IssueResult issueReplacingForMember(byte[] sessionIdHash, Long memberId,
            Duration absoluteLifetime, Duration idleTimeout);

    boolean revokeCurrent(byte[] sessionIdHash, Long memberId);

    Optional<String> revokeAllForMember(Long memberId);

    record IssueResult(Instant createdAt, Instant expiresAt, Optional<String> replacedSessionHash) {
    }
}
