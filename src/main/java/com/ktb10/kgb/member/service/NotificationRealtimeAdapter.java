package com.ktb10.kgb.member.service;

import com.ktb10.kgb.member.event.NotificationCreatedEvent;
import com.ktb10.kgb.member.event.NotificationRealtimeEnvelope;
import org.springframework.stereotype.Component;

/** 공통 Redis 이벤트를 현재 인스턴스가 보유한 SSE 연결 작업으로 변환합니다. */
@Component
public class NotificationRealtimeAdapter {

    private final NotificationStreamService notificationStreamService;

    public NotificationRealtimeAdapter(NotificationStreamService notificationStreamService) {
        this.notificationStreamService = notificationStreamService;
    }

    public void handle(NotificationRealtimeEnvelope envelope) {
        switch (envelope.eventType()) {
            case NOTIFICATION_CREATED -> notificationStreamService.publish(
                    new NotificationCreatedEvent(
                            envelope.memberId(),
                            envelope.notification(),
                            true));
            case SESSION_DISCONNECTED -> notificationStreamService.disconnectSession(
                    envelope.memberId(),
                    envelope.sessionHash());
            case MEMBER_DISCONNECTED -> notificationStreamService.disconnectMember(
                    envelope.memberId());
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 실시간 알림 이벤트 종류입니다.");
        }
    }
}
