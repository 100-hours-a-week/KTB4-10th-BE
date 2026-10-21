package com.ktb10.kgb.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.RateLimitExceededException;
import com.ktb10.kgb.credit.error.CreditErrorCode;
import com.ktb10.kgb.credit.service.CouponAttemptStore.FailureRecordResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

class CouponAttemptLimiterTest {

    private static final Long MEMBER_ID = 7L;

    private CouponAttemptStore attemptStore;
    private SimpleMeterRegistry meterRegistry;
    private CouponAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        attemptStore = mock(CouponAttemptStore.class);
        meterRegistry = new SimpleMeterRegistry();
        limiter = new CouponAttemptLimiter(attemptStore, meterRegistry);
    }

    @Test
    void allowsRequestWhenMemberIsNotBlocked() {
        when(attemptStore.remainingBlockMillis(MEMBER_ID)).thenReturn(0L);

        assertThatCode(() -> limiter.checkAllowed(MEMBER_ID)).doesNotThrowAnyException();
    }

    @Test
    void blocksRequestUsingRedisTtl() {
        when(attemptStore.remainingBlockMillis(MEMBER_ID)).thenReturn(37_001L);

        assertThatThrownBy(() -> limiter.checkAllowed(MEMBER_ID))
                .isInstanceOf(RateLimitExceededException.class)
                .extracting(exception ->
                        ((RateLimitExceededException) exception).retryAfterSeconds())
                .isEqualTo(38L);
        assertThat(meterRegistry.get("kgb.credit.coupon.blocked_requests").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void activatesBlockOnFifthFailure() {
        when(attemptStore.recordFailure(MEMBER_ID))
                .thenReturn(FailureRecordResult.activated(600_000L));

        assertThatThrownBy(() -> limiter.recordFailure(MEMBER_ID))
                .isInstanceOf(RateLimitExceededException.class)
                .extracting(exception ->
                        ((RateLimitExceededException) exception).retryAfterSeconds())
                .isEqualTo(600L);
        assertThat(meterRegistry.get("kgb.credit.coupon.invalid_attempts").counter().count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.get("kgb.credit.coupon.blocks").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void recordsFailureWithoutBlockingBelowThreshold() {
        when(attemptStore.recordFailure(MEMBER_ID))
                .thenReturn(FailureRecordResult.allowed());

        assertThatCode(() -> limiter.recordFailure(MEMBER_ID)).doesNotThrowAnyException();
        assertThat(meterRegistry.get("kgb.credit.coupon.invalid_attempts").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void resetsSharedAttemptStateAfterSuccessfulCoupon() {
        limiter.reset(MEMBER_ID);

        verify(attemptStore).reset(MEMBER_ID);
    }

    @Test
    void failsClosedWhenRedisCommandFails() {
        doThrow(new QueryTimeoutException("timeout"))
                .when(attemptStore).reset(MEMBER_ID);

        assertThatThrownBy(() -> limiter.reset(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(CreditErrorCode.COUPON_UNAVAILABLE);
        assertThat(meterRegistry.get("kgb.credit.coupon.redis_failures").counter().count())
                .isEqualTo(1.0);
    }
}
