package com.ktb10.kgb.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.member.dto.response.NotificationItemResponse;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.event.NotificationCreatedEvent;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class NotificationStreamServiceTest {

    @Mock
    private NotificationSseEmitterFactory emitterFactory;

    @Mock
    private SseEmitter firstEmitter;

    @Mock
    private SseEmitter secondEmitter;

    @Mock
    private SseEmitter otherMemberEmitter;

    private NotificationStreamService streamService;

    @BeforeEach
    void setUp() {
        streamService = new NotificationStreamService(
                emitterFactory,
                Duration.ofMinutes(25),
                3000L);
    }

    @Test
    void sendsNotificationToEveryConnectionOwnedByRecipient() throws Exception {
        given(emitterFactory.create(Duration.ofMinutes(25).toMillis()))
                .willReturn(firstEmitter, secondEmitter, otherMemberEmitter);
        streamService.subscribe(1L);
        streamService.subscribe(1L);
        streamService.subscribe(2L);
        reset(firstEmitter, secondEmitter, otherMemberEmitter);

        streamService.publish(notificationEvent(1L, "301"));

        verify(firstEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(secondEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(otherMemberEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void doesNotSendRealtimeEventWhenMemberDisabledNotifications() throws Exception {
        given(emitterFactory.create(Duration.ofMinutes(25).toMillis()))
                .willReturn(firstEmitter);
        streamService.subscribe(1L);
        reset(firstEmitter);

        streamService.publish(notificationEvent(1L, "301", false));

        verify(firstEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(streamService.connectionCount(1L)).isOne();
    }

    @Test
    void removesOnlyFailedConnectionAndContinuesOtherConnections() throws Exception {
        given(emitterFactory.create(Duration.ofMinutes(25).toMillis()))
                .willReturn(firstEmitter, secondEmitter);
        streamService.subscribe(1L);
        streamService.subscribe(1L);
        reset(firstEmitter, secondEmitter);
        doThrow(new IOException("연결 종료"))
                .when(firstEmitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        streamService.publish(notificationEvent(1L, "301"));
        streamService.publish(notificationEvent(1L, "302"));

        verify(firstEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(secondEmitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(streamService.connectionCount(1L)).isEqualTo(1);
    }

    @Test
    void heartbeatRemovesClosedConnection() throws Exception {
        given(emitterFactory.create(Duration.ofMinutes(25).toMillis()))
                .willReturn(firstEmitter);
        streamService.subscribe(1L);
        reset(firstEmitter);
        doThrow(new IllegalStateException("이미 완료된 연결"))
                .when(firstEmitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        streamService.sendHeartbeat();

        assertThat(streamService.connectionCount(1L)).isZero();
        verify(firstEmitter).completeWithError(any(IllegalStateException.class));
    }

    @Test
    void disconnectsEveryConnectionForMember() throws Exception {
        given(emitterFactory.create(Duration.ofMinutes(25).toMillis()))
                .willReturn(firstEmitter, secondEmitter);
        streamService.subscribe(1L);
        streamService.subscribe(1L);

        streamService.disconnectMember(1L);

        assertThat(streamService.connectionCount(1L)).isZero();
        verify(firstEmitter).complete();
        verify(secondEmitter).complete();
    }

    private NotificationCreatedEvent notificationEvent(Long memberId, String notificationId) {
        return notificationEvent(memberId, notificationId, true);
    }

    private NotificationCreatedEvent notificationEvent(
            Long memberId,
            String notificationId,
            boolean realtimeDeliveryEnabled) {
        return new NotificationCreatedEvent(
                memberId,
                new NotificationItemResponse(
                        notificationId,
                        NotificationType.GUIDEBOOK_COMPLETED,
                        "가이드북 생성 완료",
                        "가이드북을 확인해 주세요.",
                        NotificationReferenceType.GUIDEBOOK,
                        "101",
                        OffsetDateTime.parse("2026-10-05T00:00:00Z")),
                realtimeDeliveryEnabled);
    }
}
