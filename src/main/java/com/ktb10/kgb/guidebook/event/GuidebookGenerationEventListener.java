package com.ktb10.kgb.guidebook.event;

import com.ktb10.kgb.guidebook.client.AiClientException;
import com.ktb10.kgb.guidebook.client.AiFailureType;
import com.ktb10.kgb.guidebook.client.AiIntegrationErrorLogger;
import com.ktb10.kgb.guidebook.service.GuidebookAiTriggerService;
import com.ktb10.kgb.guidebook.service.GuidebookGenerationFailureService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 생성 작업 저장이 커밋된 뒤 AI 서버 접수를 시작합니다. */
@Component
@Profile({"local", "prod"})
public class GuidebookGenerationEventListener {

    private static final String GENERATIONS_ROUTE = "/guidebooks-generations";

    private final GuidebookAiTriggerService guidebookAiTriggerService;
    private final GuidebookGenerationFailureService generationFailureService;
    private final AiIntegrationErrorLogger errorLogger;

    public GuidebookGenerationEventListener(
            GuidebookAiTriggerService guidebookAiTriggerService,
            GuidebookGenerationFailureService generationFailureService,
            AiIntegrationErrorLogger errorLogger) {
        this.guidebookAiTriggerService = guidebookAiTriggerService;
        this.generationFailureService = generationFailureService;
        this.errorLogger = errorLogger;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(GuidebookGenerationRequestedEvent event) {
        try {
            guidebookAiTriggerService.trigger(event.jobId());
        } catch (AiClientException exception) {
            recoverAndLog(event.jobId(), exception);
        } catch (RuntimeException exception) {
            AiClientException classifiedException = new AiClientException(
                    "AI 생성 접수 처리 중 예상하지 못한 오류가 발생했습니다.",
                    exception,
                    AiFailureType.UNEXPECTED_ERROR,
                    GENERATIONS_ROUTE,
                    null,
                    0L);
            recoverAndLog(event.jobId(), classifiedException);
        }
    }

    private void recoverAndLog(Long jobId, AiClientException exception) {
        try {
            generationFailureService.failInitialSubmission(jobId, exception);
        } finally {
            errorLogger.logFailure(
                    "ai_generation_request_failed",
                    jobId,
                    null,
                    exception);
        }
    }
}
