package com.ktb10.kgb.common.security;

import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.common.error.StructuredErrorLogger;
import com.ktb10.kgb.member.service.ServiceSessionService;
import com.ktb10.kgb.tools.loadtest.LoadTestSessionAuthenticationBypass;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 서비스 세션 쿠키를 검증해 Spring Security 인증 정보로 변환합니다. */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionAuthenticationFilter.class);
    private static final List<SimpleGrantedAuthority> MEMBER_AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_MEMBER"));

    private final SessionCookieResolver cookieResolver;
    private final ServiceSessionService serviceSessionService;
    private final RestSecurityErrorWriter errorWriter;
    private final Timer authenticationTimer;
    private final Optional<LoadTestSessionAuthenticationBypass> authenticationBypass;

    public SessionAuthenticationFilter(
            SessionCookieResolver cookieResolver,
            ServiceSessionService serviceSessionService,
            RestSecurityErrorWriter errorWriter,
            MeterRegistry meterRegistry,
            Optional<LoadTestSessionAuthenticationBypass> authenticationBypass) {
        this.cookieResolver = cookieResolver;
        this.serviceSessionService = serviceSessionService;
        this.errorWriter = errorWriter;
        this.authenticationBypass = authenticationBypass;
        this.authenticationTimer = Timer.builder("kgb.auth.session.duration")
                .description("서비스 세션 인증 실행 시간")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<String> rawSessionId = cookieResolver.resolve(request);
        if (rawSessionId.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Optional<AuthenticatedMember> principal = authenticationBypass
                    .map(bypass -> bypass.authenticate(rawSessionId.get()))
                    .orElseGet(() -> authenticationTimer.record(
                            () -> serviceSessionService.authenticate(rawSessionId.get())));
            principal.ifPresent(value -> setAuthentication(request, value));
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            StructuredErrorLogger.log(
                    LOGGER,
                    "service_session_authentication_failure",
                    "Service session authentication failed",
                    request,
                    CommonErrorCode.INTERNAL_SERVER_ERROR.httpStatus().value(),
                    CommonErrorCode.INTERNAL_SERVER_ERROR.code(),
                    exception);
            errorWriter.write(request, response, CommonErrorCode.INTERNAL_SERVER_ERROR);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(
            HttpServletRequest request,
            AuthenticatedMember principal) {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        MEMBER_AUTHORITIES);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
