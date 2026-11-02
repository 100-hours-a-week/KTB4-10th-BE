package com.ktb10.kgb.guidebook.callback;

import com.ktb10.kgb.guidebook.client.AiClientException;
import com.ktb10.kgb.guidebook.client.AiFailureType;
import com.ktb10.kgb.guidebook.client.AiIntegrationErrorLogger;
import com.ktb10.kgb.guidebook.client.GuidebookAiClient;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 준비 커밋 → 트랜잭션 밖 AI 접수 → 접수 결과 커밋을 동기적으로 조정합니다. */
@Service
@Profile({"local", "prod"})
public class GenerationCallbackSubmissionService {

    private final GenerationCallbackTransactionService transactions;
    private final GenerationTimeoutRegistry timers;
    private final ObjectProvider<GuidebookAiClient> clients;
    private final AiIntegrationErrorLogger errors;

    public GenerationCallbackSubmissionService(
            GenerationCallbackTransactionService transactions,
            GenerationTimeoutRegistry timers,
            ObjectProvider<GuidebookAiClient> clients,
            AiIntegrationErrorLogger errors) {
        this.transactions = transactions;
        this.timers = timers;
        this.clients = clients;
        this.errors = errors;
    }

    @Transactional(propagation = Propagation.NEVER)
    public void submit(Long jobId) {
        var prepared = transactions.prepare(jobId);
        if (prepared == null) {
            return;
        }
        submitPrepared(prepared);
    }

    @Transactional(propagation = Propagation.NEVER)
    public void submitPrepared(GenerationCallbackTransactionService.Prepared prepared) {
        Long jobId = prepared.jobId();
        AiGenerationAcceptedResponse accepted;
        try {
            // 콜백이 202보다 먼저 와도 타이머를 취소할 수 있게 HTTP 전에 등록한다.
            timers.register(prepared);
            GuidebookAiClient client = clients.getObject();
            accepted = prepared.initialRequest() != null
                    ? client.requestGeneration(prepared.initialRequest())
                    : client.requestRegeneration(prepared.sourceAiJobId(), prepared.regenerationRequest());
        } catch (AiClientException exception) {
            if (Integer.valueOf(503).equals(exception.getUpstreamStatus())) {
                transactions.failSubmission(prepared, exception);
                errors.logFailure("ai_callback_submission_rejected", jobId, null, exception);
                return;
            }
            // 응답 유실은 AI가 이미 실행 중일 수 있어 슬롯을 임의로 반환하지 않는다.
            errors.logFailure("ai_callback_submission_uncertain", jobId, null, exception);
            return;
        } catch (RuntimeException exception) {
            AiClientException failure = new AiClientException(
                    "AI 콜백 작업 접수에 실패했습니다.", exception,
                    AiFailureType.UNEXPECTED_ERROR, "/guidebooks-generations", null, 0L);
            transactions.failSubmission(prepared, failure);
            errors.logFailure("ai_callback_submission_failed", jobId, null, failure);
            return;
        }
        // DB 실패를 AI 접수 실패로 오인하지 않는다. 등록 실패 시에도 기존 타이머는 유지한다.
        transactions.registerAccepted(prepared, accepted);
    }
}
