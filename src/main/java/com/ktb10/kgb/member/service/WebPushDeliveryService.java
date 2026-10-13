package com.ktb10.kgb.member.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.member.dto.response.WebPushNotificationPayload;
import com.ktb10.kgb.member.event.NotificationCreatedEvent;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 커밋된 알림을 활성 브라우저 구독에 제한적으로 재시도해 전송합니다. */
@Service
public class WebPushDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebPushDeliveryService.class);

    private final WebPushGateway gateway;
    private final WebPushTargetReader targetReader;
    private final WebPushDeliveryRecorder deliveryRecorder;
    private final WebPushBackoffSleeper backoffSleeper;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;

    public WebPushDeliveryService(
            WebPushGateway gateway,
            WebPushTargetReader targetReader,
            WebPushDeliveryRecorder deliveryRecorder,
            WebPushBackoffSleeper backoffSleeper,
            ObjectMapper objectMapper,
            @Value("${webpush.delivery.max-attempts:3}") int maxAttempts,
            @Value("${webpush.delivery.initial-backoff:1s}") Duration initialBackoff,
            @Value("${webpush.delivery.max-backoff:10s}") Duration maxBackoff) {
        validatePolicy(maxAttempts, initialBackoff, maxBackoff);
        this.gateway = gateway;
        this.targetReader = targetReader;
        this.deliveryRecorder = deliveryRecorder;
        this.backoffSleeper = backoffSleeper;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
    }

    public void deliver(NotificationCreatedEvent event) {
        if (!event.realtimeDeliveryEnabled()
                || !gateway.isConfigured()) {
            return;
        }

        byte[] payload = serializePayload(event);
        List<WebPushDeliveryTarget> targets = targetReader.findDeliverableTargets(
                event.recipientMemberId());
        for (WebPushDeliveryTarget target : targets) {
            deliverToTarget(event, target, payload);
        }
    }

    private void deliverToTarget(
            NotificationCreatedEvent event,
            WebPushDeliveryTarget target,
            byte[] payload) {
        WebPushSendResult result = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                result = gateway.send(
                        target,
                        payload,
                        event.notification().notificationId());
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Web Push 전송 중 예상하지 못한 오류가 발생했습니다. "
                                + "notificationId={}, subscriptionId={}",
                        event.notification().notificationId(),
                        target.subscriptionId(),
                        exception);
                deliveryRecorder.recordFailed(target.subscriptionId());
                return;
            }

            if (result.status() != WebPushSendStatus.RETRYABLE || attempt == maxAttempts) {
                break;
            }
            if (!backoffSleeper.sleep(retryDelay(result.retryAfter(), attempt))) {
                break;
            }
        }
        recordResult(event, target, result);
    }

    private void recordResult(
            NotificationCreatedEvent event,
            WebPushDeliveryTarget target,
            WebPushSendResult result) {
        if (result == null) {
            deliveryRecorder.recordFailed(target.subscriptionId());
            return;
        }
        switch (result.status()) {
            case ACCEPTED -> deliveryRecorder.recordAccepted(target.subscriptionId());
            case EXPIRED -> deliveryRecorder.recordExpired(target.subscriptionId());
            case RETRYABLE, REJECTED -> {
                deliveryRecorder.recordFailed(target.subscriptionId());
                LOGGER.warn(
                        "Web Push 전송을 완료하지 못했습니다. "
                                + "notificationId={}, subscriptionId={}, status={}, httpStatus={}",
                        event.notification().notificationId(),
                        target.subscriptionId(),
                        result.status(),
                        result.statusCode());
            }
            default -> throw new IllegalStateException(
                    "지원하지 않는 Web Push 전송 상태입니다: " + result.status());
        }
    }

    private byte[] serializePayload(NotificationCreatedEvent event) {
        try {
            return objectMapper.writeValueAsBytes(
                    WebPushNotificationPayload.from(event.notification()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Web Push payload 직렬화에 실패했습니다.", exception);
        }
    }

    private Duration retryDelay(Duration retryAfter, int attempt) {
        if (retryAfter != null) {
            return retryAfter.compareTo(maxBackoff) > 0 ? maxBackoff : retryAfter;
        }
        long multiplier = 1L << Math.min(attempt - 1, 30);
        Duration calculated = initialBackoff.multipliedBy(multiplier);
        return calculated.compareTo(maxBackoff) > 0 ? maxBackoff : calculated;
    }

    private static void validatePolicy(
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("Web Push 최대 시도 횟수는 0보다 커야 합니다.");
        }
        if (initialBackoff.isNegative() || initialBackoff.isZero()) {
            throw new IllegalArgumentException("Web Push 초기 재시도 간격은 0보다 커야 합니다.");
        }
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException(
                    "Web Push 최대 재시도 간격은 초기 간격보다 작을 수 없습니다.");
        }
    }
}
