package com.ktb10.kgb.guidebook.callback;

import com.ktb10.kgb.guidebook.client.AiClientException;
import com.ktb10.kgb.guidebook.client.AiIntegrationErrorLogger;
import com.ktb10.kgb.guidebook.client.GuidebookAiClient;
import com.ktb10.kgb.guidebook.event.GuidebookGenerationRequestedEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 생성 작업을 AI에 접수하고 콜백 타임아웃을 등록합니다. */
@Component
@Profile({"local", "prod"})
public class GenerationCallbackSubmissionListener {

    private final GenerationCallbackTransactionService transactions;
    private final GenerationTimeoutRegistry timers;
    private final ObjectProvider<GuidebookAiClient> clients;
    private final AiIntegrationErrorLogger errors;

    public GenerationCallbackSubmissionListener(
            GenerationCallbackTransactionService transactions,
            GenerationTimeoutRegistry timers,
            ObjectProvider<GuidebookAiClient> clients,
            AiIntegrationErrorLogger errors) {
        this.transactions = transactions;
        this.timers = timers;
        this.clients = clients;
        this.errors = errors;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(GuidebookGenerationRequestedEvent event) {
        GuidebookAiClient client = clients.getObject();
        GenerationCallbackTransactionService.Submission submission =
                transactions.submit(event.jobId(), client);
        if (submission == null) {
            return;
        }
        timers.register(submission.prepared());
        AiClientException failure = submission.failure();
        if (failure != null) {
            errors.logFailure(submission.failureEvent(), event.jobId(), null, failure);
        }
    }
}
