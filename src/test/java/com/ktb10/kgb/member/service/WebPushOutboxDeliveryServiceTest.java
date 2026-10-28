package com.ktb10.kgb.member.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebPushOutboxDeliveryServiceTest {

    @Mock private WebPushGateway gateway;
    @Mock private WebPushTargetReader targetReader;
    @Mock private WebPushOutboxResultService resultService;
    @Mock private WebPushOutboxMetrics metrics;

    private WebPushOutboxDeliveryService service;
    private WebPushOutboxTask task;
    private WebPushDeliveryTarget target;

    @BeforeEach
    void setUp() {
        service = new WebPushOutboxDeliveryService(
                gateway,
                targetReader,
                resultService,
                metrics,
                Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC),
                3,
                Duration.ofSeconds(5),
                Duration.ofMinutes(5));
        task = new WebPushOutboxTask(1L, 2L, 3L, 4L, "{}", 1, "token");
        target = new WebPushDeliveryTarget(4L, "endpoint", "p256dh", "auth");
    }

    @Test
    void acceptedResponseMarksTaskSent() {
        when(targetReader.findDeliverableTarget(3L, 4L)).thenReturn(Optional.of(target));
        when(gateway.send(any(), any(), any())).thenReturn(WebPushSendResult.accepted(201));
        when(resultService.markSent(task)).thenReturn(true);

        service.deliver(task);

        verify(resultService).markSent(task);
    }

    @Test
    void missingSubscriptionCancelsWithoutCallingGateway() {
        when(targetReader.findDeliverableTarget(3L, 4L)).thenReturn(Optional.empty());
        when(resultService.cancel(task, "SUBSCRIPTION_NOT_DELIVERABLE", false))
                .thenReturn(true);

        service.deliver(task);

        verify(gateway, never()).send(any(), any(), any());
        verify(resultService).cancel(task, "SUBSCRIPTION_NOT_DELIVERABLE", false);
    }

    @Test
    void retryableResponseSchedulesDbRetry() {
        when(targetReader.findDeliverableTarget(3L, 4L)).thenReturn(Optional.of(target));
        when(gateway.send(any(), any(), any()))
                .thenReturn(WebPushSendResult.retryable(null, 503));

        service.deliver(task);

        verify(resultService).scheduleRetry(any(), any(), org.mockito.ArgumentMatchers.eq("HTTP_503"));
    }

    @Test
    void expiredSubscriptionIsInvalidatedAndCancelled() {
        when(targetReader.findDeliverableTarget(3L, 4L)).thenReturn(Optional.of(target));
        when(gateway.send(any(), any(), any())).thenReturn(WebPushSendResult.expired(410));

        service.deliver(task);

        verify(resultService).cancel(task, "SUBSCRIPTION_EXPIRED", true);
        verify(resultService, never()).scheduleRetry(any(), any(), any());
    }

    @Test
    void retryableResponseAtMaxAttemptBecomesPermanentFailure() {
        WebPushOutboxTask exhausted = new WebPushOutboxTask(
                1L, 2L, 3L, 4L, "{}", 3, "token");
        when(targetReader.findDeliverableTarget(3L, 4L)).thenReturn(Optional.of(target));
        when(gateway.send(any(), any(), any()))
                .thenReturn(WebPushSendResult.retryable(Duration.ofSeconds(30), 429));

        service.deliver(exhausted);

        verify(resultService).markFailed(exhausted, "HTTP_429");
        verify(resultService, never()).scheduleRetry(any(), any(), any());
    }
}
