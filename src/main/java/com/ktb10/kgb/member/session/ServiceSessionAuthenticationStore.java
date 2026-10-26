package com.ktb10.kgb.member.session;

import java.time.Duration;
import java.util.Optional;

/** 세션 검증·유휴 시간 갱신만 담당합니다. 회원 상태는 MySQL에서 별도로 확인합니다. */
public interface ServiceSessionAuthenticationStore {

    /** 원자적으로 세션을 검증·갱신하고 회원 ID를 반환합니다. 장애는 빈 결과로 숨기지 않습니다. */
    Optional<Long> authenticateAndRecordUse(byte[] sessionIdHash, Duration idleTimeout);
}
