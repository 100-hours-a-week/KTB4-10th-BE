package com.ktb10.kgb.member.service;

import com.ktb10.kgb.member.event.NotificationCreatedEvent;
import com.ktb10.kgb.member.event.NotificationRealtimeEnvelope;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Redis fan-out을 사용하지 않는 단일 인스턴스에서 기존 로컬 SSE 전달을 유지합니다. */
@Component
@ConditionalOnProperty(
        name = "notification.redis.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class LocalNotificationRealtimePublisher implements NotificationRealtimePublisher {

    private final NotificationRealtimeAdapter adapter;
    private final Clock clock;

    public LocalNotificationRealtimePublisher(
            NotificationRealtimeAdapter adapter,
            Clock clock) {
        this.adapter = adapter;
        this.clock = clock;
    }

    @Override
    public void publishNotification(NotificationCreatedEvent event) {
        adapter.handle(NotificationRealtimeEnvelope.notification(event, Instant.now(clock)));
    }

    @Override
    public void publishSessionDisconnected(Long memberId, String sessionHash) {
        // 호출 인스턴스의 연결은 컨트롤러에서 먼저 종료하므로 추가 작업이 필요하지 않습니다.
    }

    @Override
    public void publishMemberDisconnected(Long memberId) {
        // 호출 인스턴스의 연결은 컨트롤러에서 먼저 종료하므로 추가 작업이 필요하지 않습니다.
    }
}
