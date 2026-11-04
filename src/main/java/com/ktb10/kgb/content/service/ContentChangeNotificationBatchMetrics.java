package com.ktb10.kgb.content.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** 오전 9시 행사 변경 알림 배치의 등록·처리 상태를 관측합니다. */
@Component
public class ContentChangeNotificationBatchMetrics {

    private final Counter syncNotCompletedCounter;
    private final Counter queueRejectedCounter;
    private final Counter interestedProcessingFailedCounter;
    private final Counter guidebookProcessingFailedCounter;
    private final Timer duration;

    public ContentChangeNotificationBatchMetrics(MeterRegistry meterRegistry) {
        this.syncNotCompletedCounter = resultCounter(meterRegistry, "sync_not_completed");
        this.queueRejectedCounter = resultCounter(meterRegistry, "queue_rejected");
        this.interestedProcessingFailedCounter =
                processingFailureCounter(meterRegistry, "interested_event");
        this.guidebookProcessingFailedCounter =
                processingFailureCounter(meterRegistry, "guidebook_event");
        this.duration = Timer.builder("kgb.content.change_notification.batch.duration")
                .description("일일 행사 변경 알림 배치 처리 시간")
                .register(meterRegistry);
    }

    public void recordSyncNotCompleted() {
        syncNotCompletedCounter.increment();
    }

    public void recordQueueRejected() {
        queueRejectedCounter.increment();
    }

    public void recordInterestedProcessingFailed() {
        interestedProcessingFailedCounter.increment();
    }

    public void recordGuidebookProcessingFailed() {
        guidebookProcessingFailedCounter.increment();
    }

    public void recordDuration(Duration elapsed) {
        duration.record(elapsed);
    }

    private Counter resultCounter(MeterRegistry meterRegistry, String result) {
        return Counter.builder("kgb.content.change_notification.batch.results")
                .description("일일 행사 변경 알림 배치 등록 결과")
                .tag("result", result)
                .register(meterRegistry);
    }

    private Counter processingFailureCounter(
            MeterRegistry meterRegistry,
            String processor) {
        return Counter.builder("kgb.content.change_notification.batch.failures")
                .description("일일 행사 변경 알림 프로세서 실패 횟수")
                .tag("processor", processor)
                .register(meterRegistry);
    }
}
