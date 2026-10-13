package com.ktb10.kgb.member.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.member.entity.WebPushSubscription;
import com.ktb10.kgb.member.entity.WebPushSubscriptionStatus;
import java.time.ZoneOffset;

/** 등록된 Web Push 구독의 비민감 식별 정보입니다. */
public record WebPushSubscriptionResponse(
        @JsonProperty("subscription_id")
        Long subscriptionId,
        WebPushSubscriptionStatus status,
        @JsonProperty("expiration_time")
        Long expirationTime) {

    public static WebPushSubscriptionResponse from(WebPushSubscription subscription) {
        Long expirationTime = subscription.getExpirationAt() == null
                ? null
                : subscription.getExpirationAt().toInstant(ZoneOffset.UTC).toEpochMilli();
        return new WebPushSubscriptionResponse(
                subscription.getId(),
                subscription.getStatus(),
                expirationTime);
    }
}
