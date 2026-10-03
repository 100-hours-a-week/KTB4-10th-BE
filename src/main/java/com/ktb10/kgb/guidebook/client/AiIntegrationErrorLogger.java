package com.ktb10.kgb.guidebook.client;

import com.ktb10.kgb.common.error.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

/** AI 연동 실패를 생성 작업과 연결할 수 있는 공통 형식으로 기록합니다. */
@Component
public class AiIntegrationErrorLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiIntegrationErrorLogger.class);
    private static final String TARGET_SERVICE = "ai";
    private static final String EMPTY_VALUE = "-";

    public void logFailure(
            String event,
            Long generationJobId,
            String aiJobId,
            AiClientException exception) {
        String traceId = TraceId.currentOrCreate();
        String loggedAiJobId = valueOrEmpty(aiJobId);
        String upstreamStatus = exception.getUpstreamStatus() == null
                ? EMPTY_VALUE
                : exception.getUpstreamStatus().toString();
        String exceptionType = exception.getClass().getSimpleName();
        String rootCauseType = rootCause(exception).getClass().getSimpleName();

        LoggingEventBuilder eventBuilder = LOGGER.atError()
                .setCause(exception)
                .addKeyValue("event", event)
                .addKeyValue("traceId", traceId)
                .addKeyValue("generationJobId", generationJobId)
                .addKeyValue("aiJobId", loggedAiJobId)
                .addKeyValue("targetService", TARGET_SERVICE)
                .addKeyValue("targetRoute", exception.getTargetRoute())
                .addKeyValue("upstreamStatus", upstreamStatus)
                .addKeyValue("durationMs", exception.getDurationMs())
                .addKeyValue("failureType", exception.getFailureType().code())
                .addKeyValue("retryable", exception.getFailureType().isRetryable())
                .addKeyValue("exceptionType", exceptionType)
                .addKeyValue("rootCauseType", rootCauseType);

        eventBuilder.log(
                "AI integration failed [event={}, traceId={}, generationJobId={}, aiJobId={}, "
                        + "targetService={}, targetRoute={}, upstreamStatus={}, durationMs={}, "
                        + "failureType={}, retryable={}, exceptionType={}, rootCauseType={}]",
                event,
                traceId,
                generationJobId,
                loggedAiJobId,
                TARGET_SERVICE,
                exception.getTargetRoute(),
                upstreamStatus,
                exception.getDurationMs(),
                exception.getFailureType().code(),
                exception.getFailureType().isRetryable(),
                exceptionType,
                rootCauseType);
    }

    private String valueOrEmpty(String value) {
        return value == null || value.isBlank() ? EMPTY_VALUE : value;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
