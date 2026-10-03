package com.ktb10.kgb.guidebook.event;

import com.ktb10.kgb.guidebook.client.AiClientException;
import com.ktb10.kgb.guidebook.client.AiFailureType;
import com.ktb10.kgb.guidebook.client.AiIntegrationErrorLogger;
import com.ktb10.kgb.guidebook.service.GuidebookAiTriggerService;
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
    private final AiIntegrationErrorLogger errorLogger;

    public GuidebookGenerationEventListener(
            GuidebookAiTriggerService guidebookAiTriggerService,
            AiIntegrationErrorLogger errorLogger) {
        this.guidebookAiTriggerService = guidebookAiTriggerService;
        this.errorLogger = errorLogger;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(GuidebookGenerationRequestedEvent event) {
        try {
            guidebookAiTriggerService.trigger(event.jobId());
        } catch (AiClientException exception) {
            errorLogger.logFailure(
                    "ai_generation_request_failed",
                    event.jobId(),
                    null,
                    exception);
        } catch (RuntimeException exception) {
            AiClientException classifiedException = new AiClientException(
                    "AI 생성 접수 처리 중 예상하지 못한 오류가 발생했습니다.",
                    exception,
                    AiFailureType.UNEXPECTED_ERROR,
                    GENERATIONS_ROUTE,
                    null,
                    0L);
            errorLogger.logFailure(
                    "ai_generation_request_failed",
                    event.jobId(),
                    null,
                    classifiedException);
        }
    }
}
