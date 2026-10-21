package com.ktb10.kgb.credit.service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** 쿠폰 실패 횟수와 차단 상태를 Redis 원자 연산과 TTL로 관리합니다. */
@Component
public class RedisCouponAttemptStore implements CouponAttemptStore {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(10);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(10);
    private static final String FAILURE_SUFFIX = ":failures:";
    private static final String BLOCK_SUFFIX = ":blocked:";

    private static final DefaultRedisScript<Long> RECORD_FAILURE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local blockedTtl = redis.call('PTTL', KEYS[2])
                    if blockedTtl > 0 then
                        return -blockedTtl
                    end

                    local failureCount = redis.call('INCR', KEYS[1])
                    if failureCount == 1 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end

                    if failureCount >= tonumber(ARGV[2]) then
                        redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
                        redis.call('DEL', KEYS[1])
                        return tonumber(ARGV[3])
                    end

                    return 0
                    """,
                    Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String namespace;
    private final Duration windowDuration;
    private final Duration blockDuration;
    private final int maxFailures;

    @Autowired
    public RedisCouponAttemptStore(
            StringRedisTemplate redisTemplate,
            @Value("${credit.coupon-attempt.redis.namespace:"
                    + "kgb:local:coupon:attempt}") String namespace) {
        this(redisTemplate, namespace, WINDOW_DURATION, BLOCK_DURATION, MAX_FAILURES);
    }

    RedisCouponAttemptStore(
            StringRedisTemplate redisTemplate,
            String namespace,
            Duration windowDuration,
            Duration blockDuration,
            int maxFailures) {
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate,
                "Redis 템플릿은 null일 수 없습니다.");
        this.namespace = requireNamespace(namespace);
        this.windowDuration = requirePositive(windowDuration, "실패 집계 시간");
        this.blockDuration = requirePositive(blockDuration, "차단 시간");
        if (maxFailures < 1) {
            throw new IllegalArgumentException("최대 실패 횟수는 1 이상이어야 합니다.");
        }
        this.maxFailures = maxFailures;
    }

    @Override
    public long remainingBlockMillis(Long memberId) {
        Long ttl = redisTemplate.getExpire(blockKey(memberId), TimeUnit.MILLISECONDS);
        if (ttl == null || ttl < 1L) {
            return 0L;
        }
        return ttl;
    }

    @Override
    public FailureRecordResult recordFailure(Long memberId) {
        Long result = redisTemplate.execute(
                RECORD_FAILURE_SCRIPT,
                List.of(failureKey(memberId), blockKey(memberId)),
                Long.toString(windowDuration.toMillis()),
                Integer.toString(maxFailures),
                Long.toString(blockDuration.toMillis()));
        if (result == null) {
            throw new IllegalStateException("Redis 쿠폰 실패 처리 결과가 없습니다.");
        }
        if (result < 0L) {
            return FailureRecordResult.alreadyBlocked(Math.abs(result));
        }
        if (result > 0L) {
            return FailureRecordResult.activated(result);
        }
        return FailureRecordResult.allowed();
    }

    @Override
    public void reset(Long memberId) {
        redisTemplate.delete(List.of(failureKey(memberId), blockKey(memberId)));
    }

    private String failureKey(Long memberId) {
        return namespace + FAILURE_SUFFIX + requireMemberId(memberId);
    }

    private String blockKey(Long memberId) {
        return namespace + BLOCK_SUFFIX + requireMemberId(memberId);
    }

    private static Long requireMemberId(Long memberId) {
        return Objects.requireNonNull(memberId, "회원 ID는 null일 수 없습니다.");
    }

    private static String requireNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("Redis namespace는 비어 있을 수 없습니다.");
        }
        return namespace;
    }

    private static Duration requirePositive(Duration duration, String fieldName) {
        Objects.requireNonNull(duration, fieldName + "은 null일 수 없습니다.");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + "은 0보다 커야 합니다.");
        }
        return duration;
    }
}
