package com.ktb10.kgb.guidebook.callback;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 콜백 전환은 명시적으로 켜고, 인증 비밀값과 전체 작업 제한 시간을 검증합니다. */
@Component
public class GenerationCallbackSettings {

    private final boolean enabled;
    private final String token;
    private final Duration timeout;

    public GenerationCallbackSettings(
            @Value("${ai.generation.delivery-mode:polling}") String mode,
            @Value("${ai.generation.callback-token:}") String token,
            @Value("${ai.generation.timeout:15m}") Duration timeout) {
        if (!"polling".equals(mode) && !"callback".equals(mode)) {
            throw new IllegalArgumentException("AI 전달 모드는 polling 또는 callback이어야 합니다.");
        }
        if ("callback".equals(mode) && (token == null || token.isBlank())) {
            throw new IllegalArgumentException("콜백 모드에는 AI_CALLBACK_TOKEN이 필요합니다.");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("AI 생성 제한 시간은 양수여야 합니다.");
        }
        this.enabled = "callback".equals(mode);
        this.token = token;
        this.timeout = timeout;
    }

    public boolean enabled() {
        return enabled;
    }

    public String token() {
        return token;
    }

    public Duration timeout() {
        return timeout;
    }
}
