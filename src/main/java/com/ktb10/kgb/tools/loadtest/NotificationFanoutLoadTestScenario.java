package com.ktb10.kgb.tools.loadtest;

/** 로컬 알림 fan-out 부하테스트에서 준비하고 실행할 생산자 조합입니다. */
public enum NotificationFanoutLoadTestScenario {
    NEW_EVENT_DIGEST,
    INTERESTED_EVENT_CHANGE,
    GUIDEBOOK_EVENT_CHANGE,
    TRIP_STARTING,
    MIXED
}
