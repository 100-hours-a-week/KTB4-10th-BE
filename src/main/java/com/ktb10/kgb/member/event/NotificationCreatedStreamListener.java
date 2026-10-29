package com.ktb10.kgb.member.event;

import com.ktb10.kgb.member.service.NotificationRealtimePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 인앱 알림 커밋 이후 SSE fan-out만 수행합니다. Web Push는 MySQL Outbox Worker가 처리합니다. */
@Component
public class NotificationCreatedStreamListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(NotificationCreatedStreamListener.class);

    private final NotificationRealtimePublisher notificationRealtimePublisher;

    public NotificationCreatedStreamListener(
            NotificationRealtimePublisher notificationRealtimePublisher) {
        this.notificationRealtimePublisher = notificationRealtimePublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationCreatedEvent event) {
        try {
            if (event.realtimeDeliveryEnabled()) {
                notificationRealtimePublisher.publishNotification(event);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "SSE 알림 fan-out 이벤트 발행 중 예상하지 못한 오류가 발생했습니다. "
                            + "notificationId={}, memberId={}",
                    event.notification().notificationId(),
                    event.recipientMemberId(),
                    exception);
        }
    }
}
