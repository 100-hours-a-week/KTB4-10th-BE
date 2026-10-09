package com.ktb10.kgb.member.event;

import com.ktb10.kgb.member.service.WebPushDispatchService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 알림 DB 커밋 이후 Web Push 전송 작업만 비동기 큐에 등록합니다. */
@Component
public class NotificationCreatedWebPushListener {

    private final WebPushDispatchService dispatchService;

    public NotificationCreatedWebPushListener(WebPushDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationCreatedEvent event) {
        dispatchService.dispatch(event);
    }
}
