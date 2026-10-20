package com.ktb10.kgb.member.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.member.dto.response.NotificationItemResponse;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 인스턴스 간 SSE fan-out에 사용하는 버전이 명시된 공통 이벤트 계약입니다. */
public record NotificationRealtimeEnvelope(
        @JsonProperty("schema_version")
        int schemaVersion,
        @JsonProperty("event_id")
        String eventId,
        @JsonProperty("event_type")
        NotificationRealtimeEventType eventType,
        @JsonProperty("occurred_at")
        Instant occurredAt,
        @JsonProperty("member_id")
        Long memberId,
        @JsonProperty("session_id")
        Long sessionId,
        NotificationItemResponse notification) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public NotificationRealtimeEnvelope {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("지원하지 않는 실시간 알림 이벤트 버전입니다.");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("실시간 알림 이벤트 ID는 비어 있을 수 없습니다.");
        }
        Objects.requireNonNull(eventType, "실시간 알림 이벤트 종류는 null일 수 없습니다.");
        Objects.requireNonNull(occurredAt, "실시간 알림 이벤트 발생 시각은 null일 수 없습니다.");
        Objects.requireNonNull(memberId, "실시간 알림 수신 회원 ID는 null일 수 없습니다.");
        validatePayload(eventType, sessionId, notification);
    }

    public static NotificationRealtimeEnvelope notification(
            NotificationCreatedEvent event,
            Instant occurredAt) {
        return new NotificationRealtimeEnvelope(
                CURRENT_SCHEMA_VERSION,
                "notification:" + event.notification().notificationId(),
                NotificationRealtimeEventType.NOTIFICATION_CREATED,
                occurredAt,
                event.recipientMemberId(),
                null,
                event.notification());
    }

    public static NotificationRealtimeEnvelope sessionDisconnected(
            Long memberId,
            Long sessionId,
            Instant occurredAt) {
        return new NotificationRealtimeEnvelope(
                CURRENT_SCHEMA_VERSION,
                "session-disconnected:" + UUID.randomUUID(),
                NotificationRealtimeEventType.SESSION_DISCONNECTED,
                occurredAt,
                memberId,
                sessionId,
                null);
    }

    public static NotificationRealtimeEnvelope memberDisconnected(
            Long memberId,
            Instant occurredAt) {
        return new NotificationRealtimeEnvelope(
                CURRENT_SCHEMA_VERSION,
                "member-disconnected:" + UUID.randomUUID(),
                NotificationRealtimeEventType.MEMBER_DISCONNECTED,
                occurredAt,
                memberId,
                null,
                null);
    }

    private static void validatePayload(
            NotificationRealtimeEventType eventType,
            Long sessionId,
            NotificationItemResponse notification) {
        switch (eventType) {
            case NOTIFICATION_CREATED -> {
                Objects.requireNonNull(
                        notification,
                        "알림 생성 이벤트의 알림 내용은 null일 수 없습니다.");
                if (sessionId != null) {
                    throw new IllegalArgumentException(
                            "알림 생성 이벤트에는 세션 ID를 포함할 수 없습니다.");
                }
            }
            case SESSION_DISCONNECTED -> {
                Objects.requireNonNull(
                        sessionId,
                        "세션 종료 이벤트의 세션 ID는 null일 수 없습니다.");
                if (notification != null) {
                    throw new IllegalArgumentException(
                            "세션 종료 이벤트에는 알림 내용을 포함할 수 없습니다.");
                }
            }
            case MEMBER_DISCONNECTED -> {
                if (sessionId != null || notification != null) {
                    throw new IllegalArgumentException(
                            "회원 연결 종료 이벤트에는 세션 또는 알림 내용을 포함할 수 없습니다.");
                }
            }
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 실시간 알림 이벤트 종류입니다.");
        }
    }
}
