package com.ktb10.kgb.member.event;

import static org.mockito.Mockito.verify;

import com.ktb10.kgb.member.dto.response.NotificationItemResponse;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.service.NotificationStreamService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationCreatedStreamListenerTest {

    @Mock
    private NotificationStreamService notificationStreamService;

    @Test
    void sendsCommittedNotificationThroughStream() {
        NotificationCreatedEvent event = new NotificationCreatedEvent(
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
        NotificationCreatedStreamListener listener =
                new NotificationCreatedStreamListener(notificationStreamService);

        listener.handle(event);

        verify(notificationStreamService).publish(event);
    }
}
