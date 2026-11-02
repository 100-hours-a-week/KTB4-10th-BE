package com.ktb10.kgb.content.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** 관심 행사 변경 알림이 비동기 처리 중 유실될 수 있는 실패 지점을 집계합니다. */
@Component
public class InterestedContentChangeNotificationMetrics {

    private final Counter queueRejectedCounter;
    private final Counter processingFailedCounter;
    private final Counter deliveryFailedCounter;
    private final Counter targetCounter;
    private final Counter createdCounter;
    private final Counter duplicateSkippedCounter;

    public InterestedContentChangeNotificationMetrics(MeterRegistry meterRegistry) {
        this.queueRejectedCounter = failureCounter(meterRegistry, "queue_rejected");
        this.processingFailedCounter = failureCounter(meterRegistry, "processing");
        this.deliveryFailedCounter = failureCounter(meterRegistry, "delivery");
        this.targetCounter = resultCounter(meterRegistry, "target");
        this.createdCounter = resultCounter(meterRegistry, "created");
        this.duplicateSkippedCounter = resultCounter(meterRegistry, "duplicate_skipped");
    }

    public void recordQueueRejected() {
        queueRejectedCounter.increment();
    }

    public void recordProcessingFailed() {
        processingFailedCounter.increment();
    }

    public void recordDeliveryFailed() {
        deliveryFailedCounter.increment();
    }

    public void recordTargets(int count) {
        targetCounter.increment(count);
    }

    public void recordCreated() {
        createdCounter.increment();
    }

    public void recordDuplicateSkipped() {
        duplicateSkippedCounter.increment();
    }

    private Counter failureCounter(MeterRegistry meterRegistry, String stage) {
        return Counter.builder("kgb.content.change_notification.failures")
                .description("관심 행사 변경 알림 비동기 처리 실패 횟수")
                .tag("stage", stage)
                .register(meterRegistry);
    }

    private Counter resultCounter(MeterRegistry meterRegistry, String result) {
        return Counter.builder("kgb.content.change_notification.results")
                .description("관심 행사 변경 알림 처리 결과")
                .tag("result", result)
                .register(meterRegistry);
    }
}
