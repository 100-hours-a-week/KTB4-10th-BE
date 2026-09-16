package com.ktb10.kgb.common.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 모든 HTTP 요청에 서버 생성 추적 ID를 부여합니다. */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = TraceId.getOrCreate(request);
        response.setHeader(TraceId.HEADER_NAME, traceId);
        MDC.put(MDC_KEY, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
