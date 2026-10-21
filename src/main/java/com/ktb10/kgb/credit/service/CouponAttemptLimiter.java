package com.ktb10.kgb.credit.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.RateLimitExceededException;
import com.ktb10.kgb.credit.error.CreditErrorCode;
import com.ktb10.kgb.credit.service.CouponAttemptStore.FailureRecordResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/** 회원별 잘못된 쿠폰 입력을 공유 Redis 고정 윈도우로 제한합니다. */
@Component
public class CouponAttemptLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CouponAttemptLimiter.class);

    private final CouponAttemptStore attemptStore;
    private final Counter invalidAttemptCounter;
    private final Counter blockActivatedCounter;
    private final Counter blockedRequestCounter;
    private final Counter redisFailureCounter;

    public CouponAttemptLimiter(
            CouponAttemptStore attemptStore,
            MeterRegistry meterRegistry) {
        this.attemptStore = Objects.requireNonNull(
                attemptStore,
                "쿠폰 입력 제한 저장소는 null일 수 없습니다.");
        this.invalidAttemptCounter = Counter.builder("kgb.credit.coupon.invalid_attempts")
                .description("잘못된 쿠폰 코드 입력 횟수")
                .register(meterRegistry);
        this.blockActivatedCounter = Counter.builder("kgb.credit.coupon.blocks")
                .description("쿠폰 등록 차단이 시작된 횟수")
                .register(meterRegistry);
        this.blockedRequestCounter = Counter.builder("kgb.credit.coupon.blocked_requests")
                .description("차단 중 거절된 쿠폰 등록 요청 횟수")
                .register(meterRegistry);
        this.redisFailureCounter = Counter.builder("kgb.credit.coupon.redis_failures")
                .description("쿠폰 입력 제한 Redis 명령 실패 횟수")
                .register(meterRegistry);
    }

    public void checkAllowed(Long memberId) {
        Objects.requireNonNull(memberId, "회원 ID는 null일 수 없습니다.");
        try {
            long remainingMillis = attemptStore.remainingBlockMillis(memberId);
            if (remainingMillis < 1L) {
                return;
            }
            blockedRequestCounter.increment();
            throw rateLimitExceeded(remainingMillis);
        } catch (DataAccessException | IllegalStateException exception) {
            throw unavailable(exception);
        }
    }

    public void recordFailure(Long memberId) {
        Objects.requireNonNull(memberId, "회원 ID는 null일 수 없습니다.");
        try {
            FailureRecordResult result = attemptStore.recordFailure(memberId);
            invalidAttemptCounter.increment();
            if (!result.blocked()) {
                return;
            }
            if (result.activated()) {
                blockActivatedCounter.increment();
                LOGGER.warn("잘못된 쿠폰 입력 제한으로 등록을 차단했습니다.");
            } else {
                blockedRequestCounter.increment();
            }
            throw rateLimitExceeded(result.retryAfterMillis());
        } catch (DataAccessException | IllegalStateException exception) {
            throw unavailable(exception);
        }
    }

    public void reset(Long memberId) {
        Objects.requireNonNull(memberId, "회원 ID는 null일 수 없습니다.");
        try {
            attemptStore.reset(memberId);
        } catch (DataAccessException | IllegalStateException exception) {
            throw unavailable(exception);
        }
    }

    private BusinessException unavailable(RuntimeException exception) {
        redisFailureCounter.increment();
        LOGGER.error(
                "쿠폰 입력 제한 Redis 명령에 실패했습니다. failureType={}",
                exception.getClass().getSimpleName());
        return new BusinessException(CreditErrorCode.COUPON_UNAVAILABLE);
    }

    private RateLimitExceededException rateLimitExceeded(long remainingMillis) {
        long retryAfterSeconds = Math.max(1L, (remainingMillis + 999L) / 1_000L);
        return new RateLimitExceededException(retryAfterSeconds);
    }
}
