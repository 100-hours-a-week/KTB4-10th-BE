package com.ktb10.kgb.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb10.kgb.common.error.RateLimitExceededException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CouponAttemptLimiterTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long OTHER_MEMBER_ID = 8L;
    private static final Instant START = Instant.parse("2026-10-12T00:00:00Z");

    private MutableClock clock;
    private SimpleMeterRegistry meterRegistry;
    private CouponAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        meterRegistry = new SimpleMeterRegistry();
        limiter = new CouponAttemptLimiter(clock, meterRegistry);
    }

    @Test
    void allowsFourFailuresAndBlocksFromFifthFailureForTenMinutes() {
        repeatFailure(MEMBER_ID, CouponAttemptLimiter.MAX_FAILURES - 1);

        assertThatCode(() -> limiter.checkAllowed(MEMBER_ID)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.recordFailure(MEMBER_ID))
                .isInstanceOf(RateLimitExceededException.class)
                .extracting(exception ->
                        ((RateLimitExceededException) exception).retryAfterSeconds())
                .isEqualTo(600L);
        assertThatThrownBy(() -> limiter.checkAllowed(MEMBER_ID))
                .isInstanceOf(RateLimitExceededException.class);

        assertThat(meterRegistry.get("kgb.credit.coupon.invalid_attempts").counter().count())
                .isEqualTo(5.0);
        assertThat(meterRegistry.get("kgb.credit.coupon.blocks").counter().count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.get("kgb.credit.coupon.blocked_requests").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void resetsFailureCountWhenFixedWindowExpiresBelowThreshold() {
        repeatFailure(MEMBER_ID, CouponAttemptLimiter.MAX_FAILURES - 1);
        clock.advance(CouponAttemptLimiter.WINDOW_DURATION);

        assertThatCode(() -> limiter.checkAllowed(MEMBER_ID)).doesNotThrowAnyException();
        repeatFailure(MEMBER_ID, CouponAttemptLimiter.MAX_FAILURES - 1);

        assertThatCode(() -> limiter.checkAllowed(MEMBER_ID)).doesNotThrowAnyException();
    }

    @Test
    void resetsAllStateAfterBlockExpires() {
        repeatFailure(MEMBER_ID, CouponAttemptLimiter.MAX_FAILURES - 1);
        assertThatThrownBy(() -> limiter.recordFailure(MEMBER_ID))
                .isInstanceOf(RateLimitExceededException.class);
        clock.advance(CouponAttemptLimiter.BLOCK_DURATION);

        assertThatCode(() -> limiter.checkAllowed(MEMBER_ID)).doesNotThrowAnyException();
        repeatFailure(MEMBER_ID, CouponAttemptLimiter.MAX_FAILURES - 1);

        assertThatCode(() -> limiter.checkAllowed(MEMBER_ID)).doesNotThrowAnyException();
    }

    @Test
    void successfulCouponResetClearsPreviousFailures() {
        repeatFailure(MEMBER_ID, CouponAttemptLimiter.MAX_FAILURES - 1);

        limiter.reset(MEMBER_ID);
        repeatFailure(MEMBER_ID, CouponAttemptLimiter.MAX_FAILURES - 1);

        assertThatCode(() -> limiter.checkAllowed(MEMBER_ID)).doesNotThrowAnyException();
    }

    @Test
    void oneMembersFailuresDoNotBlockAnotherMember() {
        repeatFailure(MEMBER_ID, CouponAttemptLimiter.MAX_FAILURES - 1);
        assertThatThrownBy(() -> limiter.recordFailure(MEMBER_ID))
                .isInstanceOf(RateLimitExceededException.class);

        assertThatCode(() -> limiter.checkAllowed(OTHER_MEMBER_ID)).doesNotThrowAnyException();
    }

    private void repeatFailure(Long memberId, int count) {
        for (int attempt = 0; attempt < count; attempt++) {
            limiter.recordFailure(memberId);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
