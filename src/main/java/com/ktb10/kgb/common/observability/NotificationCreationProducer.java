package com.ktb10.kgb.common.observability;

/** 일일 알림 생성 배치를 고정된 저카디널리티 태그로 구분합니다. */
public enum NotificationCreationProducer {
    NEW_EVENT_DIGEST("new_event_digest"),
    INTERESTED_EVENT_CHANGE("interested_event_change"),
    GUIDEBOOK_EVENT_CHANGE("guidebook_event_change"),
    TRIP_STARTING("trip_starting");

    private final String tagValue;

    NotificationCreationProducer(String tagValue) {
        this.tagValue = tagValue;
    }

    public String tagValue() {
        return tagValue;
    }
}
