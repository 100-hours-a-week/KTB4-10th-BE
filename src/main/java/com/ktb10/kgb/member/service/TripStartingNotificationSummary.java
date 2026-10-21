package com.ktb10.kgb.member.service;

/** 여행 일정 임박 알림 실행 결과입니다. */
public record TripStartingNotificationSummary(
        int created,
        int duplicated,
        int failed) {
}
