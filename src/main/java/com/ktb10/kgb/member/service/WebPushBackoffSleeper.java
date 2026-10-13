package com.ktb10.kgb.member.service;

import java.time.Duration;
import org.springframework.stereotype.Component;

/** 전용 비동기 작업 스레드에서 제한된 재시도 간격을 기다립니다. */
@Component
public class WebPushBackoffSleeper {

    public boolean sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
