package com.ktb10.kgb.member.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.member.dto.response.NotificationItemResponse;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.service.NotificationStreamService;
import com.ktb10.kgb.member.service.WebPushDispatchService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationCreatedStreamListenerTest {

    @Mock
    private NotificationStreamService notificationStreamService;

    @Mock
    private WebPushDispatchService webPushDispatchService;

    @Test
    void skipsWebPushWhenCommittedNotificationIsSentThroughStream() {
        NotificationCreatedEvent event = notificationEvent();
        given(notificationStreamService.publish(event)).willReturn(true);
        NotificationCreatedStreamListener listener = new NotificationCreatedStreamListener(
                notificationStreamService,
                webPushDispatchService);

        listener.handle(event);

        verify(notificationStreamService).publish(event);
        verify(webPushDispatchService, never()).dispatch(event);
    }

    @Test
    void fallsBackToWebPushWhenStreamDeliveryFails() {
        NotificationCreatedEvent event = notificationEvent();
        given(notificationStreamService.publish(event)).willReturn(false);
        NotificationCreatedStreamListener listener = new NotificationCreatedStreamListener(
                notificationStreamService,
                webPushDispatchService);

        listener.handle(event);

        verify(notificationStreamService).publish(event);
        verify(webPushDispatchService).dispatch(event);
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
                        OffsetDateTime.parse("2026-10-05T00:00:00Z")),
                true);
    }
}
