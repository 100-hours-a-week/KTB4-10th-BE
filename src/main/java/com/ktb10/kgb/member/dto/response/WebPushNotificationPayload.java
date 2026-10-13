package com.ktb10.kgb.member.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;

/** Service Worker가 알림 이동 대상을 복원하는 최소 Web Push payload입니다. */
public record WebPushNotificationPayload(
        @JsonProperty("notification_id")
        String notificationId,
        NotificationType type,
        @JsonProperty("reference_type")
        NotificationReferenceType referenceType,
        @JsonProperty("reference_id")
        String referenceId) {

    public static WebPushNotificationPayload from(NotificationItemResponse notification) {
        return new WebPushNotificationPayload(
                notification.notificationId(),
                notification.type(),
                notification.referenceType(),
                notification.referenceId());
    }
}
