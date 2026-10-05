package com.ktb10.kgb.member.event;

import com.ktb10.kgb.member.service.NotificationStreamService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 인앱 알림 커밋 이후 연결된 회원에게 SSE 이벤트를 전송합니다. */
@Component
public class NotificationCreatedStreamListener {

    private final NotificationStreamService notificationStreamService;

    public NotificationCreatedStreamListener(
            NotificationStreamService notificationStreamService) {
        this.notificationStreamService = notificationStreamService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationCreatedEvent event) {
        notificationStreamService.publish(event);
    }
}
