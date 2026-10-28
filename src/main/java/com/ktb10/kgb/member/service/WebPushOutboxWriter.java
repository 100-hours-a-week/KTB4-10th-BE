package com.ktb10.kgb.member.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.member.dto.response.NotificationItemResponse;
import com.ktb10.kgb.member.dto.response.WebPushNotificationPayload;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.WebPushSubscriptionStatus;
import com.ktb10.kgb.member.repository.WebPushOutboxRepository;
import com.ktb10.kgb.member.repository.WebPushSubscriptionRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 알림 저장 트랜잭션 안에서 활성 구독별 Web Push 작업을 영속화합니다. */
@Component
public class WebPushOutboxWriter {

    private final WebPushSubscriptionRepository subscriptionRepository;
    private final WebPushOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public WebPushOutboxWriter(
            WebPushSubscriptionRepository subscriptionRepository,
            WebPushOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPendingTasks(
            Notification notification,
            boolean realtimeDeliveryEnabled,
            LocalDateTime now) {
        if (!realtimeDeliveryEnabled) {
            return;
        }

        String payloadJson = serializePayload(notification);
        subscriptionRepository.findDeliverableByMemberId(
                        notification.getRecipient().getId(),
                        WebPushSubscriptionStatus.ACTIVE,
                        now)
                .forEach(subscription -> outboxRepository.insertPendingIfAbsent(
                        notification.getId(),
                        notification.getRecipient().getId(),
                        subscription.getId(),
                        payloadJson,
                        now));
    }

    private String serializePayload(Notification notification) {
        try {
            return objectMapper.writeValueAsString(WebPushNotificationPayload.from(
                    NotificationItemResponse.from(notification)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Web Push Outbox payload 직렬화에 실패했습니다.", exception);
        }
    }
}
