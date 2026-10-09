package com.ktb10.kgb.member.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.member.dto.response.NotificationItemResponse;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.event.NotificationCreatedEvent;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebPushDeliveryServiceTest {

    @Mock
    private WebPushGateway gateway;

    @Mock
    private WebPushTargetReader targetReader;

    @Mock
    private WebPushDeliveryRecorder deliveryRecorder;

    @Mock
    private WebPushBackoffSleeper backoffSleeper;

    @Mock
    private NotificationStreamService streamService;

    private WebPushDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new WebPushDeliveryService(
                gateway,
                targetReader,
                deliveryRecorder,
                backoffSleeper,
                streamService,
                new ObjectMapper(),
                3,
                Duration.ofSeconds(1),
                Duration.ofSeconds(10));
    }

    @Test
    void skipsWebPushWhenSseConnectionIsActive() {
        NotificationCreatedEvent event = notificationEvent();
        given(gateway.isConfigured()).willReturn(true);
        given(streamService.hasActiveConnection(1L)).willReturn(true);

        deliveryService.deliver(event);

        verify(targetReader, never()).findDeliverableTargets(1L);
        verify(gateway, never()).send(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordsAcceptedDeliveryWithoutRetry() {
        NotificationCreatedEvent event = notificationEvent();
        WebPushDeliveryTarget target = target();
        given(gateway.isConfigured()).willReturn(true);
        given(targetReader.findDeliverableTargets(1L)).willReturn(List.of(target));
        given(gateway.send(
                org.mockito.ArgumentMatchers.eq(target),
                org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("301")))
                .willReturn(WebPushSendResult.accepted(201));

        deliveryService.deliver(event);

        verify(deliveryRecorder).recordAccepted(11L);
        verify(backoffSleeper, never()).sleep(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retriesTransientFailureWithBoundedBackoff() {
        NotificationCreatedEvent event = notificationEvent();
        WebPushDeliveryTarget target = target();
        given(gateway.isConfigured()).willReturn(true);
        given(targetReader.findDeliverableTargets(1L)).willReturn(List.of(target));
        given(gateway.send(
                org.mockito.ArgumentMatchers.eq(target),
                org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("301")))
                .willReturn(WebPushSendResult.retryable(null, 503));
        given(backoffSleeper.sleep(org.mockito.ArgumentMatchers.any())).willReturn(true);

        deliveryService.deliver(event);

        verify(gateway, times(3)).send(
                org.mockito.ArgumentMatchers.eq(target),
                org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("301"));
        verify(backoffSleeper).sleep(Duration.ofSeconds(1));
        verify(backoffSleeper).sleep(Duration.ofSeconds(2));
        verify(deliveryRecorder).recordFailed(11L);
    }

    @Test
    void invalidatesExpiredSubscriptionWithoutRetry() {
        NotificationCreatedEvent event = notificationEvent();
        WebPushDeliveryTarget target = target();
        given(gateway.isConfigured()).willReturn(true);
        given(targetReader.findDeliverableTargets(1L)).willReturn(List.of(target));
        given(gateway.send(
                org.mockito.ArgumentMatchers.eq(target),
                org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("301")))
                .willReturn(WebPushSendResult.expired(410));

        deliveryService.deliver(event);

        verify(deliveryRecorder).recordExpired(11L);
        verify(backoffSleeper, never()).sleep(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsWebPushWhenMemberDisabledNotifications() {
        NotificationCreatedEvent event = new NotificationCreatedEvent(
                1L,
                notificationEvent().notification(),
                false);

        deliveryService.deliver(event);

        verify(gateway, never()).isConfigured();
        verify(targetReader, never()).findDeliverableTargets(1L);
    }

    private WebPushDeliveryTarget target() {
        return new WebPushDeliveryTarget(
                11L,
                "https://fcm.googleapis.com/example",
                "p256dh",
                "auth");
    }

    private NotificationCreatedEvent notificationEvent() {
        return new NotificationCreatedEvent(
                1L,
                new NotificationItemResponse(
                        "301",
                        NotificationType.GUIDEBOOK_COMPLETED,
                        "가이드북 생성 완료",
                        "가이드북을 확인해 주세요.",
                        NotificationReferenceType.GUIDEBOOK,
                        "101",
                        OffsetDateTime.parse("2026-10-09T00:00:00Z")),
                true);
    }
}
