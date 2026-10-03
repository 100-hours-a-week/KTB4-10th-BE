package com.ktb10.kgb.guidebook.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AiIntegrationErrorLoggerTest {

    private final AiIntegrationErrorLogger logger = new AiIntegrationErrorLogger();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void logsFailureWithTraceAndGenerationIdentifiers(CapturedOutput output) {
        MDC.put("traceId", "trace-ai-161");
        String sensitiveUpstreamBody = "sensitive-ai-response-body";
        AiClientException exception = new AiClientException(
                "AI 서버가 오류 응답을 반환했습니다. body=" + sensitiveUpstreamBody,
                new IllegalStateException("upstream detail=" + sensitiveUpstreamBody),
                AiFailureType.UPSTREAM_5XX,
                "/guidebooks-generations/{jobId}",
                502,
                321L);

        logger.logFailure(
                "ai_generation_status_sync_failed",
                301L,
                "ai-job-301",
                exception);

        assertThat(output)
                .contains("event=ai_generation_status_sync_failed")
                .contains("traceId=trace-ai-161")
                .contains("generationJobId=301")
                .contains("aiJobId=ai-job-301")
                .contains("targetService=ai")
                .contains("targetRoute=/guidebooks-generations/{jobId}")
                .contains("upstreamStatus=502")
                .contains("durationMs=321")
                .contains("failureType=upstream_5xx")
                .contains("retryable=true")
                .contains("rootCauseType=IllegalStateException")
                .contains("sanitized exception type=AiClientException")
                .contains("sanitized exception type=IllegalStateException")
                .doesNotContain(sensitiveUpstreamBody)
                .doesNotContain("upstream detail");
    }
}
