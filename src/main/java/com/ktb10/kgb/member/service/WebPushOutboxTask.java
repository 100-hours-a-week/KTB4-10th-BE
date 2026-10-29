package com.ktb10.kgb.member.service;

/** DB 선점이 끝난 뒤 외부 Push 호출에 사용하는 불변 작업 스냅샷입니다. */
public record WebPushOutboxTask(
        Long id,
        Long notificationId,
        Long recipientMemberId,
        Long subscriptionId,
        String payloadJson,
        int attemptCount,
        String claimToken) {
}
