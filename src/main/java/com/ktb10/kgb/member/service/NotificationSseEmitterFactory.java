package com.ktb10.kgb.member.service;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 회원별 SSE 연결 객체 생성을 분리합니다. */
@Component
public class NotificationSseEmitterFactory {

    public SseEmitter create(long timeoutMillis) {
        return new SseEmitter(timeoutMillis);
    }
}
