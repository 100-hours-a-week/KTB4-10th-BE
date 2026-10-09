package com.ktb10.kgb.member.event;

import com.ktb10.kgb.member.service.NotificationStreamService;
import com.ktb10.kgb.member.service.WebPushDispatchService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 인앱 알림 커밋 이후 SSE를 우선 전송하고 실패하면 Web Push로 fallback합니다. */
@Component
public class NotificationCreatedStreamListener {

    private final NotificationStreamService notificationStreamService;
    private final WebPushDispatchService webPushDispatchService;

    public NotificationCreatedStreamListener(
            NotificationStreamService notificationStreamService,
            WebPushDispatchService webPushDispatchService) {
        this.notificationStreamService = notificationStreamService;
        this.webPushDispatchService = webPushDispatchService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationCreatedEvent event) {
        boolean deliveredThroughStream = notificationStreamService.publish(event);
        if (!deliveredThroughStream) {
            webPushDispatchService.dispatch(event);
        }
    }
}
