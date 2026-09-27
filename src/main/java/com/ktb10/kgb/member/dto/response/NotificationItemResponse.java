package com.ktb10.kgb.member.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 알림 목록의 한 항목입니다. */
public record NotificationItemResponse(
        @JsonProperty("notification_id")
        String notificationId,
        NotificationType type,
        String title,
        String body,
        @JsonProperty("reference_type")
        NotificationReferenceType referenceType,
        @JsonProperty("reference_id")
        String referenceId,
        @JsonProperty("created_at")
        OffsetDateTime createdAt) {

    public static NotificationItemResponse from(Notification notification) {
        return new NotificationItemResponse(
                notification.getId().toString(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
}
