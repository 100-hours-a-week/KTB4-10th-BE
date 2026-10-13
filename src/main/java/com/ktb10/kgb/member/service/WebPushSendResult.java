package com.ktb10.kgb.member.service;

import java.time.Duration;

/** Push 서비스의 응답을 애플리케이션 전송 정책으로 분류한 결과입니다. */
public record WebPushSendResult(
        WebPushSendStatus status,
        Duration retryAfter,
        Integer statusCode) {

    public static WebPushSendResult accepted(int statusCode) {
        return new WebPushSendResult(WebPushSendStatus.ACCEPTED, null, statusCode);
    }

    public static WebPushSendResult expired(int statusCode) {
        return new WebPushSendResult(WebPushSendStatus.EXPIRED, null, statusCode);
    }

    public static WebPushSendResult retryable(Duration retryAfter, Integer statusCode) {
        return new WebPushSendResult(WebPushSendStatus.RETRYABLE, retryAfter, statusCode);
    }

    public static WebPushSendResult rejected(Integer statusCode) {
        return new WebPushSendResult(WebPushSendStatus.REJECTED, null, statusCode);
    }
}
