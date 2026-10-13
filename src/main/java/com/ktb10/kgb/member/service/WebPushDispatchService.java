package com.ktb10.kgb.member.service;

import com.ktb10.kgb.member.event.NotificationCreatedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** 알림 트랜잭션과 분리된 제한 큐에 Web Push 작업을 등록합니다. */
@Service
public class WebPushDispatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebPushDispatchService.class);
    private static final int MAX_TRACKED_NOTIFICATION_IDS = 10_000;

    private final Executor executor;
    private final WebPushDeliveryService deliveryService;
    private final Map<String, Boolean> scheduledNotificationIds = new LinkedHashMap<>();

    public WebPushDispatchService(
            @Qualifier("webPushTaskExecutor") Executor executor,
            WebPushDeliveryService deliveryService) {
        this.executor = executor;
        this.deliveryService = deliveryService;
    }

    public void dispatch(NotificationCreatedEvent event) {
        String notificationId = event.notification().notificationId();
        if (!markScheduled(notificationId)) {
            return;
        }
        try {
            executor.execute(() -> deliveryService.deliver(event));
        } catch (RejectedExecutionException exception) {
            forget(notificationId);
            LOGGER.warn(
                    "Web Push 작업 큐가 가득 차 전송을 등록하지 못했습니다. "
                            + "notificationId={}, memberId={}",
                    notificationId,
                    event.recipientMemberId());
        }
    }

    private synchronized boolean markScheduled(String notificationId) {
        if (scheduledNotificationIds.containsKey(notificationId)) {
            return false;
        }
        if (scheduledNotificationIds.size() >= MAX_TRACKED_NOTIFICATION_IDS) {
            String oldestId = scheduledNotificationIds.keySet().iterator().next();
            scheduledNotificationIds.remove(oldestId);
        }
        scheduledNotificationIds.put(notificationId, Boolean.TRUE);
        return true;
    }

    private synchronized void forget(String notificationId) {
        scheduledNotificationIds.remove(notificationId);
    }
}
