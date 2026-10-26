package com.ktb10.kgb.member.session;

import com.ktb10.kgb.member.entity.MemberStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** 인증과 별도로 세션 발급·교체·폐기의 저장소 경계를 정의합니다. */
public interface ServiceSessionLifecycleStore {

    /**
     * 회원의 이전 세션을 폐기하고 새 세션을 원자적으로 저장합니다.
     * 원문 토큰과 전역적으로 고유한 숫자 세션 ID의 생성은 호출자 책임입니다.
     * 이미 사용 중인 토큰 해시는 덮어쓰지 않으며 장애 시 MySQL로 우회하지 않습니다.
     */
    IssueResult issueReplacingForMember(byte[] sessionIdHash, Long sessionId, Long memberId,
            MemberStatus memberStatus, Duration absoluteLifetime, Duration idleTimeout);

    /** 소유권이 일치하는 세션만 폐기합니다. 이전 세션의 요청으로 새 세션을 삭제하지 않습니다. */
    boolean revokeCurrent(byte[] sessionIdHash, Long sessionId, Long memberId);

    /** 회원의 현재 세션과 인덱스를 폐기합니다. 회원 탈퇴 여부 검증은 호출자 책임입니다. */
    Optional<Long> revokeAllForMember(Long memberId);

    /** 저장소 시각 기준 발급 시각과 절대 만료, 교체된 세션 식별자입니다. Push/SSE 정리는 별도입니다. */
    record IssueResult(Instant createdAt, Instant expiresAt, Optional<Long> replacedSessionId) {
    }
}
