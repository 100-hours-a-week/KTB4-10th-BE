package com.ktb10.kgb.guidebook.callback;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 콜백 인증 비밀값과 전체 작업 제한 시간을 검증합니다. */
@Component
public class GenerationCallbackSettings {

    private final String token;
    private final Duration timeout;

    public GenerationCallbackSettings(
            @Value("${ai.generation.callback-token:}") String token,
            @Value("${ai.generation.timeout:15m}") Duration timeout) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("AI_CALLBACK_TOKEN이 필요합니다.");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("AI 생성 제한 시간은 양수여야 합니다.");
        }
        this.token = token;
        this.timeout = timeout;
    }

    public String token() {
        return token;
    }

    public Duration timeout() {
        return timeout;
    }
}
