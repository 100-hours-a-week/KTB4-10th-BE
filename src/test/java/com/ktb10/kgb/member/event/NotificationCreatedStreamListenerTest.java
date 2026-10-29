package com.ktb10.kgb.member.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.ktb10.kgb.member.dto.response.NotificationItemResponse;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.service.NotificationRealtimePublisher;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationCreatedStreamListenerTest {

    @Mock
    private NotificationRealtimePublisher notificationRealtimePublisher;

    @Test
    void publishesCommittedNotificationThroughStream() {
        NotificationCreatedEvent event = notificationEvent();
        NotificationCreatedStreamListener listener = new NotificationCreatedStreamListener(
                notificationRealtimePublisher);

        listener.handle(event);

        verify(notificationRealtimePublisher).publishNotification(event);
    }

    @Test
    void skipsRealtimeDeliveryWhenDisabled() {
        NotificationCreatedEvent event = notificationEvent(false);
        NotificationCreatedStreamListener listener = new NotificationCreatedStreamListener(
                notificationRealtimePublisher);

        listener.handle(event);

        verify(notificationRealtimePublisher, never()).publishNotification(event);
    }

    @Test
    void doesNotPropagateUnexpectedStreamException() {
        NotificationCreatedEvent event = notificationEvent();
        doThrow(new IllegalStateException("SSE 전송 실패"))
                .when(notificationRealtimePublisher).publishNotification(event);
        NotificationCreatedStreamListener listener = new NotificationCreatedStreamListener(
                notificationRealtimePublisher);

        assertDoesNotThrow(() -> listener.handle(event));

        verify(notificationRealtimePublisher).publishNotification(event);
    }

    private NotificationCreatedEvent notificationEvent() {
        return notificationEvent(true);
    }

    private NotificationCreatedEvent notificationEvent(boolean realtimeDeliveryEnabled) {
        return new NotificationCreatedEvent(
                1L,
                new NotificationItemResponse(
                        "301",
                        NotificationType.GUIDEBOOK_COMPLETED,
                        "가이드북 생성 완료",
                        "가이드북을 확인해 주세요.",
                        NotificationReferenceType.GUIDEBOOK,
                        "101",
                        OffsetDateTime.parse("2026-10-05T00:00:00Z")),
                realtimeDeliveryEnabled);
    }
}
