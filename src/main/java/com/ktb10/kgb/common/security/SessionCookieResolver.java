package com.ktb10.kgb.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 요청 쿠키에서 서비스 세션 ID를 하나만 추출합니다. */
@Component
public class SessionCookieResolver {

    public static final String COOKIE_NAME = "KGB_SESSION";

    public Optional<String> resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        String resolvedValue = null;
        for (Cookie cookie : cookies) {
            if (!COOKIE_NAME.equals(cookie.getName())) {
                continue;
            }
            if (resolvedValue != null) {
                return Optional.empty();
            }
            resolvedValue = cookie.getValue();
        }

        if (resolvedValue == null || resolvedValue.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(resolvedValue);
    }
}
