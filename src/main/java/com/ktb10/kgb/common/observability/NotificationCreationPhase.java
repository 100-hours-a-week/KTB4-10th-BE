package com.ktb10.kgb.common.observability;

/** 알림 생성 배치의 최초 실행과 제한 재실행을 구분합니다. */
public enum NotificationCreationPhase {
    INITIAL("initial"),
    RECONCILIATION("reconciliation");

    private final String tagValue;

    NotificationCreationPhase(String tagValue) {
        this.tagValue = tagValue;
    }

    public String tagValue() {
        return tagValue;
    }
}
