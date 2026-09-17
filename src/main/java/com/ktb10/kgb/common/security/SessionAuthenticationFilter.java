package com.ktb10.kgb.common.security;

import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.common.error.TraceId;
import com.ktb10.kgb.member.service.ServiceSessionService;
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

    public SessionAuthenticationFilter(
            SessionCookieResolver cookieResolver,
            ServiceSessionService serviceSessionService,
            RestSecurityErrorWriter errorWriter) {
        this.cookieResolver = cookieResolver;
        this.serviceSessionService = serviceSessionService;
        this.errorWriter = errorWriter;
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
            serviceSessionService.authenticate(rawSessionId.get())
                    .ifPresent(principal -> setAuthentication(request, principal));
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            String traceId = TraceId.getOrCreate(request);
            LOGGER.error("Service session authentication failed [traceId={}]", traceId, exception);
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
