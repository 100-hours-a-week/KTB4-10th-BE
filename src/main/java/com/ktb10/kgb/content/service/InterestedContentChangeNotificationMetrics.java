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

    public InterestedContentChangeNotificationMetrics(MeterRegistry meterRegistry) {
        this.queueRejectedCounter = failureCounter(meterRegistry, "queue_rejected");
        this.processingFailedCounter = failureCounter(meterRegistry, "processing");
        this.deliveryFailedCounter = failureCounter(meterRegistry, "delivery");
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

    private Counter failureCounter(MeterRegistry meterRegistry, String stage) {
        return Counter.builder("kgb.content.change_notification.failures")
                .description("관심 행사 변경 알림 비동기 처리 실패 횟수")
                .tag("stage", stage)
                .register(meterRegistry);
    }
}
