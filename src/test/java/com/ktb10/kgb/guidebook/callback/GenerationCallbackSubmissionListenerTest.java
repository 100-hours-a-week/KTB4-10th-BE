package com.ktb10.kgb.guidebook.callback;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.guidebook.client.AiClientException;
import com.ktb10.kgb.guidebook.client.AiFailureType;
import com.ktb10.kgb.guidebook.client.AiIntegrationErrorLogger;
import com.ktb10.kgb.guidebook.client.GuidebookAiClient;
import com.ktb10.kgb.guidebook.event.GuidebookGenerationRequestedEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class GenerationCallbackSubmissionListenerTest {

    @Mock
    private GenerationCallbackTransactionService transactions;
    @Mock
    private GenerationTimeoutRegistry timers;
    @Mock
    private ObjectProvider<GuidebookAiClient> clients;
    @Mock
    private GuidebookAiClient client;
    @Mock
    private AiIntegrationErrorLogger errors;

    @Test
    void submitsWithinTransactionServiceAndRegistersTimeout() {
        var prepared = prepared();
        var submission = new GenerationCallbackTransactionService.Submission(
                prepared, null, null);
        when(clients.getObject()).thenReturn(client);
        when(transactions.submit(301L, client)).thenReturn(submission);

        listener().handle(new GuidebookGenerationRequestedEvent(301L));

        verify(transactions).submit(301L, client);
        verify(timers).register(prepared);
        verifyNoInteractions(errors);
    }

    @Test
    void uncertainSubmissionKeepsTimerAndLogsFailure() {
        var prepared = prepared();
        var exception = new AiClientException("response lost", null,
                AiFailureType.CONNECTION_ERROR, "/guidebooks-generations", null, 1);
        var submission = new GenerationCallbackTransactionService.Submission(
                prepared, exception, "ai_callback_submission_uncertain");
        when(clients.getObject()).thenReturn(client);
        when(transactions.submit(301L, client)).thenReturn(submission);

        listener().handle(new GuidebookGenerationRequestedEvent(301L));

        verify(timers).register(prepared);
        verify(timers, never()).cancel("301-0");
        verify(errors).logFailure(
                "ai_callback_submission_uncertain", 301L, null, exception);
    }

    @Test
    void duplicateSubmissionDoesNotStartAnotherTimer() {
        when(clients.getObject()).thenReturn(client);
        when(transactions.submit(301L, client)).thenReturn(null);

        listener().handle(new GuidebookGenerationRequestedEvent(301L));

        verifyNoInteractions(timers, errors);
    }

    private GenerationCallbackTransactionService.Prepared prepared() {
        return new GenerationCallbackTransactionService.Prepared(
                301L, 0, Instant.now().plusSeconds(900), null, null, null);
    }

    private GenerationCallbackSubmissionListener listener() {
        return new GenerationCallbackSubmissionListener(transactions, timers, clients, errors);
    }
}
