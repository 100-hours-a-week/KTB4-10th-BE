package com.ktb10.kgb.member.controller;

import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.common.security.CsrfTokenLifecycle;
import com.ktb10.kgb.common.security.SessionCookieManager;
import com.ktb10.kgb.member.service.ServiceSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 현재 서비스 세션을 종료하는 인증 API를 제공합니다. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final ServiceSessionService serviceSessionService;
    private final SessionCookieManager sessionCookieManager;
    private final CsrfTokenLifecycle csrfTokenLifecycle;

    public AuthController(
            ServiceSessionService serviceSessionService,
            SessionCookieManager sessionCookieManager,
            CsrfTokenLifecycle csrfTokenLifecycle) {
        this.serviceSessionService = serviceSessionService;
        this.sessionCookieManager = sessionCookieManager;
        this.csrfTokenLifecycle = csrfTokenLifecycle;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthenticatedMember member,
            HttpServletRequest request,
            HttpServletResponse response) {
        serviceSessionService.revokeCurrent(member.memberId(), member.sessionId());
        csrfTokenLifecycle.clear(request, response);
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                sessionCookieManager.expire().toString());
        return ResponseEntity.noContent().build();
    }
}
