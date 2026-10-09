package com.ktb10.kgb.member.service;

/** 비동기 Web Push 전송에 필요한 구독 스냅샷입니다. */
public record WebPushDeliveryTarget(
        Long subscriptionId,
        String endpoint,
        String p256dh,
        String authSecret) {
}
