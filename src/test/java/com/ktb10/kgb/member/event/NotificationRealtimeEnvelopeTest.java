package com.ktb10.kgb.member.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb10.kgb.member.dto.response.NotificationItemResponse;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class NotificationRealtimeEnvelopeTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-20T01:00:00Z");

    @Test
    void createsVersionedNotificationEnvelope() {
        NotificationRealtimeEnvelope envelope = NotificationRealtimeEnvelope.notification(
                notificationEvent(),
                OCCURRED_AT);

        assertThat(envelope.schemaVersion()).isEqualTo(2);
        assertThat(envelope.eventId()).isEqualTo("notification:301");
        assertThat(envelope.eventType())
                .isEqualTo(NotificationRealtimeEventType.NOTIFICATION_CREATED);
        assertThat(envelope.memberId()).isEqualTo(1L);
        assertThat(envelope.notification().notificationId()).isEqualTo("301");
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        assertThatThrownBy(() -> new NotificationRealtimeEnvelope(
                3,
                "notification:301",
                NotificationRealtimeEventType.NOTIFICATION_CREATED,
                OCCURRED_AT,
                1L,
                null,
                notificationEvent().notification()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 실시간 알림 이벤트 버전입니다.");
    }

    @Test
    void requiresSessionIdForSessionDisconnectEvent() {
        assertThatThrownBy(() -> new NotificationRealtimeEnvelope(
                2,
                "session-disconnected:test",
                NotificationRealtimeEventType.SESSION_DISCONNECTED,
                OCCURRED_AT,
                1L,
                null,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("세션 종료 이벤트의 세션 ID는 null일 수 없습니다.");
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
