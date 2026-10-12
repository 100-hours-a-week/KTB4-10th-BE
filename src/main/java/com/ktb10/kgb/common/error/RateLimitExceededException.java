package com.ktb10.kgb.common.error;

import java.io.Serial;

/** 호출 제한이 해제될 때까지 남은 시간을 공통 오류 응답에 전달합니다. */
public class RateLimitExceededException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(CommonErrorCode.RATE_LIMIT_EXCEEDED);
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("재시도 대기 시간은 1초 이상이어야 합니다.");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
