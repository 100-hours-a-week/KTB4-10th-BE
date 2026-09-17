package com.ktb10.kgb.common.security;

import com.ktb10.kgb.member.entity.MemberStatus;
import java.security.Principal;
import java.util.Objects;

/** 서비스 세션 검증을 마친 회원의 요청 범위 인증 정보입니다. */
public record AuthenticatedMember(Long memberId, MemberStatus status, Long sessionId)
        implements Principal {

    public AuthenticatedMember {
        requirePositive(memberId, "회원 ID");
        Objects.requireNonNull(status, "회원 상태는 null일 수 없습니다.");
        requirePositive(sessionId, "세션 ID");
    }

    @Override
    public String getName() {
        return memberId.toString();
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "는 양수여야 합니다.");
        }
    }
}
