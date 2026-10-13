package com.ktb10.kgb.member.service;

/** Web Push 한 건의 전송 처리 상태입니다. */
public enum WebPushSendStatus {
    ACCEPTED,
    EXPIRED,
    RETRYABLE,
    REJECTED
}
