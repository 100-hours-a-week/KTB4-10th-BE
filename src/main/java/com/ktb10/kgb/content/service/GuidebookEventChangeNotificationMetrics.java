package com.ktb10.kgb.content.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** 가이드북 포함 행사 변경·취소 알림의 처리 결과를 집계합니다. */
@Component
public class GuidebookEventChangeNotificationMetrics {

    private final Counter targetCounter;
    private final Counter createdCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;

    public GuidebookEventChangeNotificationMetrics(MeterRegistry meterRegistry) {
        this.targetCounter = counter(meterRegistry, "target");
        this.createdCounter = counter(meterRegistry, "created");
        this.duplicateCounter = counter(meterRegistry, "duplicate");
        this.failedCounter = counter(meterRegistry, "failed");
    }

    public void recordTargets(int count) {
        targetCounter.increment(count);
    }

    public void recordCreated() {
        createdCounter.increment();
    }

    public void recordDuplicate() {
        duplicateCounter.increment();
    }

    public void recordFailed() {
        failedCounter.increment();
    }

    private Counter counter(MeterRegistry meterRegistry, String result) {
        return Counter.builder("kgb.guidebook.event_change_notification.results")
                .description("가이드북 포함 행사 변경 알림 처리 결과")
                .tag("result", result)
                .register(meterRegistry);
    }
}
