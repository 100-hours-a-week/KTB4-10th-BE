package com.ktb10.kgb.member.event;

import com.ktb10.kgb.member.service.NotificationRealtimePublisher;
import com.ktb10.kgb.member.service.WebPushDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 인앱 알림 커밋 이후 SSE와 Web Push를 서로 독립적으로 전송합니다. */
@Component
public class NotificationCreatedStreamListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(NotificationCreatedStreamListener.class);

    private final NotificationRealtimePublisher notificationRealtimePublisher;
    private final WebPushDispatchService webPushDispatchService;

    public NotificationCreatedStreamListener(
            NotificationRealtimePublisher notificationRealtimePublisher,
            WebPushDispatchService webPushDispatchService) {
        this.notificationRealtimePublisher = notificationRealtimePublisher;
        this.webPushDispatchService = webPushDispatchService;
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

        try {
            webPushDispatchService.dispatch(event);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Web Push 작업 등록 중 예상하지 못한 오류가 발생했습니다. "
                            + "notificationId={}, memberId={}",
                    event.notification().notificationId(),
                    event.recipientMemberId(),
                    exception);
        }
    }
}
