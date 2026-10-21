package com.ktb10.kgb.member.event;

/** Redis Pub/Sub으로 전달하는 실시간 알림 제어 이벤트 종류입니다. */
public enum NotificationRealtimeEventType {
    NOTIFICATION_CREATED,
    SESSION_DISCONNECTED,
    MEMBER_DISCONNECTED
}
