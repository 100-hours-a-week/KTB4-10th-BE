package com.ktb10.kgb.content.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** 신규 행사 일일 묶음 알림의 생성량과 처리 시간을 기록합니다. */
@Component
public class NewEventDigestNotificationMetrics {

    private final Counter createdCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;
    private final Counter skippedCounter;
    private final DistributionSummary eventCount;
    private final DistributionSummary targetCount;
    private final Timer duration;

    public NewEventDigestNotificationMetrics(MeterRegistry meterRegistry) {
        this.createdCounter = resultCounter(meterRegistry, "created");
        this.duplicateCounter = resultCounter(meterRegistry, "duplicate");
        this.failedCounter = resultCounter(meterRegistry, "failed");
        this.skippedCounter = resultCounter(meterRegistry, "skipped");
        this.eventCount = DistributionSummary.builder(
                        "kgb.content.new_event_notification.events")
                .description("기준일 신규 행사 수")
                .register(meterRegistry);
        this.targetCount = DistributionSummary.builder(
                        "kgb.content.new_event_notification.targets")
                .description("신규 행사 묶음 알림 대상 회원 수")
                .register(meterRegistry);
        this.duration = Timer.builder("kgb.content.new_event_notification.duration")
                .description("신규 행사 묶음 알림 생성 처리 시간")
                .register(meterRegistry);
    }

    public void record(NewEventDigestNotificationSummary summary, Duration elapsed) {
        eventCount.record(summary.newEventCount());
        targetCount.record(summary.targetMemberCount());
        createdCounter.increment(summary.createdCount());
        duplicateCounter.increment(summary.duplicateCount());
        failedCounter.increment(summary.failedCount());
        duration.record(elapsed);
    }

    public void recordSkipped() {
        skippedCounter.increment();
    }

    private Counter resultCounter(MeterRegistry meterRegistry, String result) {
        return Counter.builder("kgb.content.new_event_notification.results")
                .description("신규 행사 묶음 알림 처리 결과")
                .tag("result", result)
                .register(meterRegistry);
    }
}
