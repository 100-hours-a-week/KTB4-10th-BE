package com.ktb10.kgb.member.service;

import static org.mockito.Mockito.verify;

import com.ktb10.kgb.member.dto.response.NotificationItemResponse;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.event.NotificationCreatedEvent;
import com.ktb10.kgb.member.event.NotificationRealtimeEnvelope;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationRealtimeAdapterTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-20T01:00:00Z");

    @Mock
    private NotificationStreamService notificationStreamService;

    @Test
    void routesEveryEnvelopeTypeToLocalStreamService() {
        NotificationRealtimeAdapter adapter = new NotificationRealtimeAdapter(
                notificationStreamService);
        NotificationCreatedEvent event = notificationEvent();

        adapter.handle(NotificationRealtimeEnvelope.notification(event, OCCURRED_AT));
        adapter.handle(NotificationRealtimeEnvelope.sessionDisconnected(1L, "000000000000000000000000000000000000000000000000000000000000000b", OCCURRED_AT));
        adapter.handle(NotificationRealtimeEnvelope.memberDisconnected(1L, OCCURRED_AT));

        verify(notificationStreamService).publish(event);
        verify(notificationStreamService).disconnectSession(1L, "000000000000000000000000000000000000000000000000000000000000000b");
        verify(notificationStreamService).disconnectMember(1L);
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
                        OffsetDateTime.parse("2026-10-20T01:00:00Z")),
                true);
    }
}
