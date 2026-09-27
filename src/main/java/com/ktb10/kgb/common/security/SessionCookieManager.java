package com.ktb10.kgb.common.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** 로그인과 로그아웃에서 동일한 속성의 서비스 세션 쿠키를 생성합니다. */
@Component
public class SessionCookieManager {

    private static final Duration COOKIE_MAX_AGE = Duration.ofHours(8);

    private final boolean secureCookie;

    public SessionCookieManager(
            @Value("${SESSION_COOKIE_SECURE:true}") boolean secureCookie) {
        this.secureCookie = secureCookie;
    }

    public ResponseCookie issue(String rawSessionId) {
        return baseCookie(rawSessionId)
                .maxAge(COOKIE_MAX_AGE)
                .build();
    }

    public ResponseCookie expire() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(SessionCookieResolver.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/");
    }
}
