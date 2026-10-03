package com.ktb10.kgb.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Pattern;

/** 요청 범위의 서버 생성 추적 ID를 관리합니다. */
public final class TraceId {

    public static final String ATTRIBUTE_NAME = TraceId.class.getName();
    public static final String HEADER_NAME = "X-Trace-Id";
    private static final int MAX_LENGTH = 64;
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1," + MAX_LENGTH + "}");

    private TraceId() {
    }

    public static String create() {
        return UUID.randomUUID().toString();
    }

    public static String getOrCreate(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE_NAME);
        if (value instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }

        String requestedTraceId = request.getHeader(HEADER_NAME);
        String traceId = isValid(requestedTraceId) ? requestedTraceId : create();
        request.setAttribute(ATTRIBUTE_NAME, traceId);
        return traceId;
    }

    static boolean isValid(String value) {
        return value != null && SAFE_VALUE.matcher(value).matches();
    }
}
