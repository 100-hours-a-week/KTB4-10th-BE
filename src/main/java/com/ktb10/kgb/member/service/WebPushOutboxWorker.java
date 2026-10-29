package com.ktb10.kgb.member.service;

import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** MySQL Outbox를 polling해 Web Push를 발송하는 다중 인스턴스 안전 Worker입니다. */
@Component
public class WebPushOutboxWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebPushOutboxWorker.class);

    private final WebPushGateway gateway;
    private final WebPushOutboxClaimService claimService;
    private final WebPushOutboxDeliveryService deliveryService;
    private final WebPushOutboxMetrics metrics;
    private final boolean enabled;
    private final int batchSize;
    private final Duration lease;

    public WebPushOutboxWorker(
            WebPushGateway gateway,
            WebPushOutboxClaimService claimService,
            WebPushOutboxDeliveryService deliveryService,
            WebPushOutboxMetrics metrics,
            @Value("${webpush.outbox.worker.enabled:true}") boolean enabled,
            @Value("${webpush.outbox.worker.batch-size:50}") int batchSize,
            @Value("${webpush.outbox.worker.lease:1m}") Duration lease) {
        if (batchSize <= 0 || lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("Web Push Outbox Worker 설정이 올바르지 않습니다.");
        }
        this.gateway = gateway;
        this.claimService = claimService;
        this.deliveryService = deliveryService;
        this.metrics = metrics;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.lease = lease;
    }

    @Scheduled(fixedDelayString = "${webpush.outbox.worker.poll-interval-millis:5000}")
    public void poll() {
        metrics.refresh();
        if (!enabled || !gateway.isConfigured()) {
            return;
        }
        Timer.Sample batchSample = null;
        try {
            List<WebPushOutboxTask> tasks =
                    metrics.recordClaim(() -> claimService.claim(batchSize, lease));
            metrics.recordClaimedBatchSize(tasks.size());
            if (!tasks.isEmpty()) {
                batchSample = metrics.startBatch();
            }
            for (WebPushOutboxTask task : tasks) {
                try {
                    metrics.recordDelivery(() -> deliveryService.deliver(task));
                } catch (RuntimeException exception) {
                    metrics.recordWorkerError();
                    LOGGER.error(
                            "Web Push Outbox 작업 처리 중 예상하지 못한 오류가 발생했습니다. outboxId={}",
                            task.id(),
                            exception);
                }
            }
            if (!tasks.isEmpty()) {
                LOGGER.info("Web Push Outbox batch 처리를 마쳤습니다. claimed={}", tasks.size());
            }
        } catch (RuntimeException exception) {
            metrics.recordWorkerError();
            throw exception;
        } finally {
            if (batchSample != null) {
                metrics.stopBatch(batchSample);
            }
            metrics.refresh();
        }
    }
}
