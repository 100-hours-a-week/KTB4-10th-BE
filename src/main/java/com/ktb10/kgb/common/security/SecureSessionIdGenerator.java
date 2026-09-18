package com.ktb10.kgb.common.security;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** 브라우저에 전달할 추측 불가능한 서비스 세션 ID를 생성합니다. */
@Component
public class SecureSessionIdGenerator {

    private static final int SESSION_ID_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[SESSION_ID_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
