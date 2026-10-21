package com.ktb10.kgb.credit.service;

/** 쿠폰 실패 횟수와 차단 상태를 여러 애플리케이션 인스턴스가 공유하도록 저장합니다. */
public interface CouponAttemptStore {

    long remainingBlockMillis(Long memberId);

    FailureRecordResult recordFailure(Long memberId);

    void reset(Long memberId);

    /** 실패 기록 결과와 차단까지 남은 시간을 전달합니다. */
    record FailureRecordResult(
            boolean blocked,
            boolean activated,
            long retryAfterMillis) {

        static FailureRecordResult allowed() {
            return new FailureRecordResult(false, false, 0L);
        }

        static FailureRecordResult activated(long retryAfterMillis) {
            return new FailureRecordResult(true, true, retryAfterMillis);
        }

        static FailureRecordResult alreadyBlocked(long retryAfterMillis) {
            return new FailureRecordResult(true, false, retryAfterMillis);
        }
    }
}
