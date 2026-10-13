package com.ktb10.kgb.credit.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ktb10.kgb.common.error.RateLimitExceededException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 회원별 잘못된 쿠폰 입력을 고정 윈도우로 집계하고 일시 차단합니다. */
@Component
public class CouponAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW_DURATION = Duration.ofMinutes(10);
    static final Duration BLOCK_DURATION = Duration.ofMinutes(10);

    private static final Logger LOGGER = LoggerFactory.getLogger(CouponAttemptLimiter.class);
    private static final long MAXIMUM_TRACKED_MEMBERS = 100_000L;
    private static final Duration CACHE_EXPIRATION = Duration.ofMinutes(20);

    private final Clock clock;
    private final Cache<Long, AttemptState> attempts;
    private final Counter invalidAttemptCounter;
    private final Counter blockActivatedCounter;
    private final Counter blockedRequestCounter;

    public CouponAttemptLimiter(Clock clock, MeterRegistry meterRegistry) {
        this.clock = Objects.requireNonNull(clock, "시계는 null일 수 없습니다.");
        this.attempts = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_TRACKED_MEMBERS)
                .expireAfterAccess(CACHE_EXPIRATION)
                .build();
        this.invalidAttemptCounter = Counter.builder("kgb.credit.coupon.invalid_attempts")
                .description("잘못된 쿠폰 코드 입력 횟수")
                .register(meterRegistry);
        this.blockActivatedCounter = Counter.builder("kgb.credit.coupon.blocks")
                .description("쿠폰 등록 차단이 시작된 횟수")
                .register(meterRegistry);
        this.blockedRequestCounter = Counter.builder("kgb.credit.coupon.blocked_requests")
                .description("차단 중 거절된 쿠폰 등록 요청 횟수")
                .register(meterRegistry);
    }

    public void checkAllowed(Long memberId) {
        Objects.requireNonNull(memberId, "회원 ID는 null일 수 없습니다.");
        Instant now = clock.instant();
        AtomicReference<Instant> blockedUntil = new AtomicReference<>();
        AtomicReference<Boolean> expiredBlock = new AtomicReference<>(false);

        attempts.asMap().computeIfPresent(memberId, (key, state) -> {
            if (state.isBlocked(now)) {
                blockedUntil.set(state.blockedUntil());
                return state;
            }
            if (state.isExpired(now)) {
                expiredBlock.set(state.blockedUntil() != null);
                return null;
            }
            return state;
        });

        if (Boolean.TRUE.equals(expiredBlock.get())) {
            LOGGER.info("쿠폰 등록 차단이 해제되었습니다. memberId={}", memberId);
        }
        if (blockedUntil.get() != null) {
            blockedRequestCounter.increment();
            throw rateLimitExceeded(now, blockedUntil.get());
        }
    }

    public void recordFailure(Long memberId) {
        Objects.requireNonNull(memberId, "회원 ID는 null일 수 없습니다.");
        Instant now = clock.instant();
        AtomicReference<BlockDecision> decision = new AtomicReference<>(BlockDecision.allowed());

        attempts.asMap().compute(memberId, (key, current) -> {
            if (current != null && current.isBlocked(now)) {
                decision.set(BlockDecision.alreadyBlocked(current.blockedUntil()));
                return current;
            }

            AttemptState active = current;
            if (active == null || active.isExpired(now)) {
                active = AttemptState.start(now.plus(WINDOW_DURATION));
            }

            int nextFailureCount = active.failureCount() + 1;
            if (nextFailureCount >= MAX_FAILURES) {
                Instant blockedUntil = now.plus(BLOCK_DURATION);
                decision.set(BlockDecision.activated(blockedUntil));
                return active.block(nextFailureCount, blockedUntil);
            }
            return active.increment(nextFailureCount);
        });

        invalidAttemptCounter.increment();
        if (decision.get().blockedUntil() == null) {
            return;
        }
        if (decision.get().activated()) {
            blockActivatedCounter.increment();
            LOGGER.warn(
                    "잘못된 쿠폰 입력 제한으로 등록을 차단했습니다. memberId={}, blockedUntil={}",
                    memberId,
                    decision.get().blockedUntil());
        } else {
            blockedRequestCounter.increment();
        }
        throw rateLimitExceeded(now, decision.get().blockedUntil());
    }

    public void reset(Long memberId) {
        Objects.requireNonNull(memberId, "회원 ID는 null일 수 없습니다.");
        attempts.invalidate(memberId);
    }

    private RateLimitExceededException rateLimitExceeded(Instant now, Instant blockedUntil) {
        long millis = Math.max(1L, Duration.between(now, blockedUntil).toMillis());
        long retryAfterSeconds = Math.max(1L, (millis + 999L) / 1_000L);
        return new RateLimitExceededException(retryAfterSeconds);
    }

    private record AttemptState(
            int failureCount,
            Instant windowExpiresAt,
            Instant blockedUntil) {

        static AttemptState start(Instant windowExpiresAt) {
            return new AttemptState(0, windowExpiresAt, null);
        }

        AttemptState increment(int nextFailureCount) {
            return new AttemptState(nextFailureCount, windowExpiresAt, null);
        }

        AttemptState block(int nextFailureCount, Instant nextBlockedUntil) {
            return new AttemptState(nextFailureCount, windowExpiresAt, nextBlockedUntil);
        }

        boolean isBlocked(Instant now) {
            return blockedUntil != null && now.isBefore(blockedUntil);
        }

        boolean isExpired(Instant now) {
            Instant expiration = blockedUntil == null ? windowExpiresAt : blockedUntil;
            return !now.isBefore(expiration);
        }
    }

    private record BlockDecision(boolean activated, Instant blockedUntil) {

        static BlockDecision allowed() {
            return new BlockDecision(false, null);
        }

        static BlockDecision activated(Instant blockedUntil) {
            return new BlockDecision(true, blockedUntil);
        }

        static BlockDecision alreadyBlocked(Instant blockedUntil) {
            return new BlockDecision(false, blockedUntil);
        }
    }
}
