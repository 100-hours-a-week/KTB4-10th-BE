package com.ktb10.kgb.member.service;

/** SSE 연결이 인스턴스 메모리에서 제거된 원인을 제한된 메트릭 태그로 제공합니다. */
public enum SseConnectionCloseReason {
    COMPLETION,
    TIMEOUT,
    ERROR,
    INITIAL_SEND_FAILURE,
    NOTIFICATION_SEND_FAILURE,
    HEARTBEAT_FAILURE,
    EVICTED,
    SESSION_DISCONNECT,
    MEMBER_DISCONNECT
}
