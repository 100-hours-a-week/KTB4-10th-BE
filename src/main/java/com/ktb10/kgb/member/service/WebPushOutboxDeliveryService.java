package com.ktb10.kgb.member.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 선점된 Outbox 작업을 한 번 전송하고 DB 기반 재시도 상태로 기록합니다. */
@Service
public class WebPushOutboxDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebPushOutboxDeliveryService.class);

    private final WebPushGateway gateway;
    private final WebPushTargetReader targetReader;
    private final WebPushOutboxResultService resultService;
    private final WebPushOutboxMetrics metrics;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;

    public WebPushOutboxDeliveryService(
            WebPushGateway gateway,
            WebPushTargetReader targetReader,
            WebPushOutboxResultService resultService,
            WebPushOutboxMetrics metrics,
            Clock clock,
            @Value("${webpush.outbox.worker.max-attempts:3}") int maxAttempts,
            @Value("${webpush.outbox.worker.initial-backoff:5s}") Duration initialBackoff,
            @Value("${webpush.outbox.worker.max-backoff:5m}") Duration maxBackoff) {
        this.gateway = gateway;
        this.targetReader = targetReader;
        this.resultService = resultService;
        this.metrics = metrics;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
    }

    public void deliver(WebPushOutboxTask task) {
        WebPushDeliveryTarget target = targetReader.findDeliverableTarget(
                        task.recipientMemberId(), task.subscriptionId())
                .orElse(null);
        if (target == null) {
            record("cancelled", resultService.cancel(
                    task, "SUBSCRIPTION_NOT_DELIVERABLE", false));
            return;
        }

        try {
            WebPushSendResult result = gateway.send(
                    target,
                    task.payloadJson().getBytes(StandardCharsets.UTF_8),
                    task.notificationId().toString());
            handle(task, result);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Web Push Outbox 전송 중 통신 오류가 발생했습니다. outboxId={}, attempt={}",
                    task.id(),
                    task.attemptCount());
            retryOrFail(task, null, "GATEWAY_EXCEPTION");
        }
    }

    private void handle(WebPushOutboxTask task, WebPushSendResult result) {
        switch (result.status()) {
            case ACCEPTED -> record("sent", resultService.markSent(task));
            case EXPIRED -> record("cancelled", resultService.cancel(
                    task, "SUBSCRIPTION_EXPIRED", true));
            case RETRYABLE -> retryOrFail(
                    task, result.retryAfter(), httpErrorCode(result.statusCode()));
            case REJECTED -> record("failed_permanent", resultService.markFailed(
                    task, httpErrorCode(result.statusCode())));
            default -> throw new IllegalStateException(
                    "지원하지 않는 Web Push 전송 상태입니다: " + result.status());
        }
    }

    private void retryOrFail(
            WebPushOutboxTask task,
            Duration retryAfter,
            String errorCode) {
        if (task.attemptCount() >= maxAttempts) {
            record("failed_permanent", resultService.markFailed(task, errorCode));
            return;
        }
        Duration delay = retryDelay(retryAfter, task.attemptCount());
        record("retry_wait", resultService.scheduleRetry(
                task, LocalDateTime.now(clock).plus(delay), errorCode));
    }

    private Duration retryDelay(Duration retryAfter, int attempt) {
        if (retryAfter != null) {
            return retryAfter.compareTo(maxBackoff) > 0 ? maxBackoff : retryAfter;
        }
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 30);
        Duration calculated = initialBackoff.multipliedBy(multiplier);
        return calculated.compareTo(maxBackoff) > 0 ? maxBackoff : calculated;
    }

    private String httpErrorCode(Integer statusCode) {
        return statusCode == null ? "PUSH_SERVICE_ERROR" : "HTTP_" + statusCode;
    }

    private void record(String outcome, boolean updated) {
        metrics.recordOutcome(updated ? outcome : "stale_result_ignored");
    }
}
