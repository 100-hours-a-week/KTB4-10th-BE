package com.ktb10.kgb.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.servlet.HandlerMapping;

/** 오류 로그의 공통 필드와 요청 경로 표현을 통일합니다. */
public final class StructuredErrorLogger {

    private StructuredErrorLogger() {
    }

    public static void log(
            Logger logger,
            String event,
            String logMessage,
            HttpServletRequest request,
            int status,
            String errorCode,
            Throwable exception) {
        String traceId = TraceId.getOrCreate(request);
        String route = route(request);
        String exceptionType = exception.getClass().getSimpleName();
        String rootCauseType = rootCause(exception).getClass().getSimpleName();

        LoggingEventBuilder eventBuilder = logger.atError()
                .setCause(exception)
                .addKeyValue("event", event)
                .addKeyValue("traceId", traceId)
                .addKeyValue("method", request.getMethod())
                .addKeyValue("route", route)
                .addKeyValue("status", status)
                .addKeyValue("errorCode", errorCode)
                .addKeyValue("exceptionType", exceptionType)
                .addKeyValue("rootCauseType", rootCauseType);

        eventBuilder.log(
                "{} [event={}, traceId={}, method={}, route={}, status={}, errorCode={}, "
                        + "exceptionType={}, rootCauseType={}]",
                logMessage,
                event,
                traceId,
                request.getMethod(),
                route,
                status,
                errorCode,
                exceptionType,
                rootCauseType);
    }

    static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return pattern.toString();
        }
        return request.getRequestURI();
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
