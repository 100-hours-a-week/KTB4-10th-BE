package com.ktb10.kgb.member.service;

import com.ktb10.kgb.member.entity.WebPushOutboxStatus;
import com.ktb10.kgb.member.repository.WebPushOutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Outbox 적체와 처리 결과를 낮은 카디널리티 메트릭으로 제공합니다. */
@Component
public class WebPushOutboxMetrics {

    private static final List<WebPushOutboxStatus> WAITING = List.of(
            WebPushOutboxStatus.PENDING,
            WebPushOutboxStatus.RETRY_WAIT);

    private final MeterRegistry meterRegistry;
    private final WebPushOutboxRepository repository;
    private final Clock clock;
    private final AtomicLong waitingCount = new AtomicLong();
    private final AtomicLong processingCount = new AtomicLong();
    private final AtomicLong oldestWaitingSeconds = new AtomicLong();
    private final ConcurrentHashMap<String, io.micrometer.core.instrument.Counter> outcomes =
            new ConcurrentHashMap<>();
    private final Timer claimDuration;
    private final Timer deliveryDuration;
    private final Timer batchDuration;
    private final DistributionSummary claimedBatchSize;
    private final Counter workerErrors;

    public WebPushOutboxMetrics(
            MeterRegistry meterRegistry,
            WebPushOutboxRepository repository,
            Clock clock) {
        this.meterRegistry = meterRegistry;
        this.repository = repository;
        this.clock = clock;
        Gauge.builder("kgb.notification.webpush.outbox.waiting", waitingCount, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("kgb.notification.webpush.outbox.processing", processingCount, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder(
                        "kgb.notification.webpush.outbox.oldest.waiting.seconds",
                        oldestWaitingSeconds,
                        AtomicLong::get)
                .register(meterRegistry);
        this.claimDuration = Timer.builder("kgb.notification.webpush.outbox.claim.duration")
                .description("Web Push Outbox batch 선점 시간")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.deliveryDuration = Timer.builder("kgb.notification.webpush.outbox.delivery.duration")
                .description("Web Push Outbox 단일 작업 발송과 결과 기록 시간")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.batchDuration = Timer.builder("kgb.notification.webpush.outbox.batch.duration")
                .description("Web Push Outbox Worker의 batch 전체 처리 시간")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.claimedBatchSize = DistributionSummary.builder(
                        "kgb.notification.webpush.outbox.claimed.batch.size")
                .description("Web Push Outbox Worker가 한 번에 선점한 작업 수")
                .register(meterRegistry);
        this.workerErrors = Counter.builder("kgb.notification.webpush.outbox.worker.errors")
                .description("Web Push Outbox Worker의 예상하지 못한 작업 오류 수")
                .register(meterRegistry);
    }

    public void refresh() {
        LocalDateTime now = LocalDateTime.now(clock);
        waitingCount.set(repository.countByStatuses(WAITING));
        processingCount.set(repository.countByStatuses(List.of(WebPushOutboxStatus.PROCESSING)));
        long age = repository.findOldestCreatedAtByStatuses(WAITING)
                .map(createdAt -> Math.max(0L, Duration.between(createdAt, now).toSeconds()))
                .orElse(0L);
        oldestWaitingSeconds.set(age);
    }

    public void recordOutcome(String outcome) {
        outcomes.computeIfAbsent(
                        outcome,
                        key -> meterRegistry.counter(
                                "kgb.notification.webpush.outbox.results", "outcome", key))
                .increment();
    }

    public <T> T recordClaim(Supplier<T> action) {
        return claimDuration.record(action);
    }

    public void recordDelivery(Runnable action) {
        deliveryDuration.record(action);
    }

    public Timer.Sample startBatch() {
        return Timer.start(meterRegistry);
    }

    public void stopBatch(Timer.Sample sample) {
        sample.stop(batchDuration);
    }

    public void recordClaimedBatchSize(int size) {
        claimedBatchSize.record(size);
    }

    public void recordWorkerError() {
        workerErrors.increment();
    }
}
