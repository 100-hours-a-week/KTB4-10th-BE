package com.ktb10.kgb.content.service;

/** 기준일 신규 행사 묶음 알림 생성 결과입니다. */
public record NewEventDigestNotificationSummary(
        long newEventCount,
        int targetMemberCount,
        int createdCount,
        int duplicateCount,
        int failedCount) {
}
