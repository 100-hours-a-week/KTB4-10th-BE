package com.ktb10.kgb.member.entity;

/** Web Push Outbox 작업의 처리 상태입니다. */
public enum WebPushOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY_WAIT,
    SENT,
    FAILED_PERMANENT,
    CANCELLED
}
