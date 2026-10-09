package com.ktb10.kgb.member.service;

import com.ktb10.kgb.member.entity.WebPushSubscription;
import com.ktb10.kgb.member.entity.WebPushSubscriptionStatus;
import com.ktb10.kgb.member.repository.WebPushSubscriptionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 네트워크 전송과 분리된 짧은 트랜잭션으로 구독 전송 결과를 기록합니다. */
@Service
public class WebPushDeliveryRecorder {

    private final WebPushSubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public WebPushDeliveryRecorder(
            WebPushSubscriptionRepository subscriptionRepository,
            Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAccepted(Long subscriptionId) {
        WebPushSubscription subscription = findActive(subscriptionId);
        if (subscription != null) {
            subscription.markDeliverySucceeded(LocalDateTime.now(clock));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(Long subscriptionId) {
        WebPushSubscription subscription = findActive(subscriptionId);
        if (subscription != null) {
            subscription.markDeliveryFailed(LocalDateTime.now(clock));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordExpired(Long subscriptionId) {
        WebPushSubscription subscription = findActive(subscriptionId);
        if (subscription != null) {
            subscription.invalidate(LocalDateTime.now(clock));
        }
    }

    private WebPushSubscription findActive(Long subscriptionId) {
        return subscriptionRepository.findByIdAndStatus(
                        subscriptionId,
                        WebPushSubscriptionStatus.ACTIVE)
                .orElse(null);
    }
}
