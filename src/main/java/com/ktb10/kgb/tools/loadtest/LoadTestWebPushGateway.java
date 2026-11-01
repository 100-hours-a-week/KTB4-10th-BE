package com.ktb10.kgb.tools.loadtest;

import com.ktb10.kgb.member.service.WebPushDeliveryTarget;
import com.ktb10.kgb.member.service.WebPushGateway;
import com.ktb10.kgb.member.service.WebPushSendResult;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 외부 Push Service를 호출하지 않고 Worker 결과를 재현하는 로컬 부하테스트 전용 Gateway입니다. */
@Component
@Profile("local & loadtest")
public class LoadTestWebPushGateway implements WebPushGateway {

    private static final int RATIO_BASE = 100;

    private final boolean enabled;
    private final int delayedUpperBound;
    private final int retry429UpperBound;
    private final int retry503UpperBound;
    private final int timeoutUpperBound;
    private final Duration delay;
    private final Duration timeout;
    private final AtomicLong sequence = new AtomicLong();

    public LoadTestWebPushGateway(
            @Value("${webpush.load-test-gateway.enabled:true}") boolean enabled,
            @Value("${webpush.load-test-gateway.delayed-percent:0}") int delayedPercent,
            @Value("${webpush.load-test-gateway.retry-429-percent:0}") int retry429Percent,
            @Value("${webpush.load-test-gateway.retry-503-percent:0}") int retry503Percent,
            @Value("${webpush.load-test-gateway.timeout-percent:0}") int timeoutPercent,
            @Value("${webpush.load-test-gateway.delay:200ms}") Duration delay,
            @Value("${webpush.load-test-gateway.timeout:2s}") Duration timeout) {
        validateRatios(delayedPercent, retry429Percent, retry503Percent, timeoutPercent);
        validateDuration(delay, "지연 시간");
        validateDuration(timeout, "timeout 시간");
        this.enabled = enabled;
        this.delayedUpperBound = delayedPercent;
        this.retry429UpperBound = delayedUpperBound + retry429Percent;
        this.retry503UpperBound = retry429UpperBound + retry503Percent;
        this.timeoutUpperBound = retry503UpperBound + timeoutPercent;
        this.delay = delay;
        this.timeout = timeout;
    }

    @Override
    public boolean isConfigured() {
        return enabled;
    }

    @Override
    public WebPushSendResult send(
            WebPushDeliveryTarget target,
            byte[] payload,
            String topic) {
        int slot = (int) Math.floorMod(sequence.getAndIncrement(), RATIO_BASE);
        if (slot < delayedUpperBound) {
            pause(delay);
            return WebPushSendResult.accepted(201);
        }
        if (slot < retry429UpperBound) {
            return WebPushSendResult.retryable(Duration.ofSeconds(1), 429);
        }
        if (slot < retry503UpperBound) {
            return WebPushSendResult.retryable(null, 503);
        }
        if (slot < timeoutUpperBound) {
            pause(timeout);
            throw new LoadTestWebPushTimeoutException();
        }
        return WebPushSendResult.accepted(201);
    }

    private void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LoadTestWebPushTimeoutException();
        }
    }

    private void validateRatios(int... ratios) {
        int total = 0;
        for (int ratio : ratios) {
            if (ratio < 0 || ratio > RATIO_BASE) {
                throw new IllegalArgumentException("Web Push 부하테스트 응답 비율은 0~100이어야 합니다.");
            }
            total += ratio;
        }
        if (total > RATIO_BASE) {
            throw new IllegalArgumentException("Web Push 부하테스트 응답 비율 합은 100 이하여야 합니다.");
        }
    }

    private void validateDuration(Duration duration, String name) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + "은 음수일 수 없습니다.");
        }
    }

    private static final class LoadTestWebPushTimeoutException extends RuntimeException {

        private LoadTestWebPushTimeoutException() {
            super("부하테스트용 Web Push timeout입니다.");
        }
    }
}
