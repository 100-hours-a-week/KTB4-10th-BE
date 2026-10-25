package com.ktb10.kgb.member.session;

import com.ktb10.kgb.common.security.AuthenticatedMember;
import java.time.Duration;
import java.util.Optional;

/** 서비스 세션 인증에 필요한 검증과 활동 갱신의 저장소 경계입니다. */
public interface ServiceSessionAuthenticationStore {

    /**
     * 세션과 회원의 유효성을 검증하고, 성공한 경우에만 마지막 사용 시각을 갱신합니다.
     *
     * <p>구현체는 검증과 갱신을 하나의 원자적 작업으로 처리해야 합니다. 세션 부재·만료·폐기 또는
     * 탈퇴 회원은 빈 결과를 반환하고, 저장소 장애는 인증 실패로 숨기지 않고 호출자에게 전달합니다.
     * 원문 쿠키 대신 해시를 전달하며, 활동 갱신으로 절대 만료 시각을 연장하지 않습니다.
     */
    Optional<AuthenticatedMember> authenticateAndRecordUse(byte[] sessionIdHash, Duration idleTimeout);
}
