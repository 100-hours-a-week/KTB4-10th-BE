package com.ktb10.kgb.member.service;

import com.ktb10.kgb.member.entity.WebPushOutbox;
import com.ktb10.kgb.member.entity.WebPushOutboxStatus;
import com.ktb10.kgb.member.entity.WebPushSubscription;
import com.ktb10.kgb.member.entity.WebPushSubscriptionStatus;
import com.ktb10.kgb.member.repository.WebPushOutboxRepository;
import com.ktb10.kgb.member.repository.WebPushSubscriptionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 선점 token이 일치하는 작업만 갱신해 lease 만료 뒤의 오래된 결과 덮어쓰기를 막습니다. */
@Service
public class WebPushOutboxResultService {

    private final WebPushOutboxRepository outboxRepository;
    private final WebPushSubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public WebPushOutboxResultService(
            WebPushOutboxRepository outboxRepository,
            WebPushSubscriptionRepository subscriptionRepository,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSent(WebPushOutboxTask task) {
        WebPushOutbox outbox = findClaimed(task);
        if (outbox == null) {
            return false;
        }
        findActiveSubscription(task.subscriptionId())
                .ifPresent(subscription -> subscription.markDeliverySucceeded(now()));
        outbox.markSent(now());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean scheduleRetry(
            WebPushOutboxTask task,
            LocalDateTime nextAttemptAt,
            String errorCode) {
        WebPushOutbox outbox = findClaimed(task);
        if (outbox == null) {
            return false;
        }
        findActiveSubscription(task.subscriptionId())
                .ifPresent(subscription -> subscription.markDeliveryFailed(now()));
        outbox.scheduleRetry(nextAttemptAt, errorCode, now());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(WebPushOutboxTask task, String errorCode) {
        WebPushOutbox outbox = findClaimed(task);
        if (outbox == null) {
            return false;
        }
        findActiveSubscription(task.subscriptionId())
                .ifPresent(subscription -> subscription.markDeliveryFailed(now()));
        outbox.markFailed(errorCode, now());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cancel(WebPushOutboxTask task, String reason, boolean invalidate) {
        WebPushOutbox outbox = findClaimed(task);
        if (outbox == null) {
            return false;
        }
        if (invalidate) {
            findActiveSubscription(task.subscriptionId())
                    .ifPresent(subscription -> subscription.invalidate(now()));
        }
        outbox.cancel(reason, now());
        return true;
    }

    private WebPushOutbox findClaimed(WebPushOutboxTask task) {
        return outboxRepository.findByIdAndStatusAndClaimToken(
                        task.id(), WebPushOutboxStatus.PROCESSING, task.claimToken())
                .orElse(null);
    }

    private java.util.Optional<WebPushSubscription> findActiveSubscription(Long id) {
        return subscriptionRepository.findByIdAndStatus(id, WebPushSubscriptionStatus.ACTIVE);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
