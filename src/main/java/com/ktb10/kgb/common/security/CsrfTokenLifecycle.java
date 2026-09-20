package com.ktb10.kgb.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/** 인증 경계에서 이전 CSRF 토큰을 폐기합니다. */
@Component
public class CsrfTokenLifecycle {

    private final CsrfTokenRepository csrfTokenRepository;

    public CsrfTokenLifecycle(CsrfTokenRepository csrfTokenRepository) {
        this.csrfTokenRepository = csrfTokenRepository;
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        csrfTokenRepository.saveToken(null, request, response);
    }
}
