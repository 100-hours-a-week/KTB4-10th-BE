package com.ktb10.kgb.guidebook.callback;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.guidebook.client.AiClientException;
import com.ktb10.kgb.guidebook.client.AiFailureType;
import com.ktb10.kgb.guidebook.client.AiGenerationStatus;
import com.ktb10.kgb.guidebook.client.AiIntegrationErrorLogger;
import com.ktb10.kgb.guidebook.client.GuidebookAiClient;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class GenerationCallbackSubmissionServiceTest {

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
    @Mock
    private AiGuidebookRequest request;
    @InjectMocks
    private GenerationCallbackSubmissionService submissions;

    @Test
    void preparesThenRegistersTimerBeforeHttpAndPersistsAcceptedResponse() {
        var prepared = prepared();
        var accepted = new AiGenerationAcceptedResponse("ai-301", AiGenerationStatus.PENDING);
        when(transactions.prepare(301L)).thenReturn(prepared);
        when(clients.getObject()).thenReturn(client);
        when(client.requestGeneration(request)).thenReturn(accepted);

        submissions.submit(301L);

        var order = inOrder(transactions, timers, client);
        order.verify(transactions).prepare(301L);
        order.verify(timers).register(prepared);
        order.verify(client).requestGeneration(request);
        order.verify(transactions).registerAccepted(prepared, accepted);
        verifyNoInteractions(errors);
    }

    @Test
    void uncertainSubmissionKeepsTimerAndDoesNotFailOrRegisterAccepted() {
        var prepared = prepared();
        var failure = new AiClientException("response lost", null,
                AiFailureType.CONNECTION_ERROR, "/guidebooks-generations", null, 1);
        when(transactions.prepare(301L)).thenReturn(prepared);
        when(clients.getObject()).thenReturn(client);
        when(client.requestGeneration(request)).thenThrow(failure);

        submissions.submit(301L);

        verify(timers).register(prepared);
        verify(timers, never()).cancel(any());
        verify(transactions, never()).failSubmission(any(), any());
        verify(transactions, never()).registerAccepted(any(), any());
        verify(errors).logFailure("ai_callback_submission_uncertain", 301L, null, failure);
    }

    @Test
    void duplicateSubmissionDoesNotCallAiOrStartAnotherTimer() {
        submissions.submit(301L);
        verifyNoInteractions(timers, clients, client, errors);
    }

    @Test
    void explicit503FailsSubmissionInsteadOfWaitingForTimeout() {
        var prepared = prepared();
        var failure = new AiClientException("queue full", null,
                AiFailureType.UPSTREAM_5XX, "/guidebooks-generations", 503, 1);
        when(transactions.prepare(301L)).thenReturn(prepared);
        when(clients.getObject()).thenReturn(client);
        when(client.requestGeneration(request)).thenThrow(failure);

        submissions.submit(301L);

        verify(transactions).failSubmission(prepared, failure);
        verify(transactions, never()).registerAccepted(any(), any());
        verify(errors).logFailure("ai_callback_submission_rejected", 301L, null, failure);
    }

    @Test
    void timerRegistrationFailureStopsBeforeHttpAndFailsTheClaimedAttempt() {
        var prepared = prepared();
        when(transactions.prepare(301L)).thenReturn(prepared);
        doThrow(new IllegalStateException("scheduler unavailable")).when(timers).register(prepared);

        submissions.submit(301L);

        verify(transactions).failSubmission(org.mockito.ArgumentMatchers.eq(prepared), any());
        // 종료 이벤트가 타이머를 취소한다. 이미 콜백이 시작된 작업의 타이머를 임의로 끄지 않는다.
        verify(timers, never()).cancel(any());
        verifyNoInteractions(clients, client);
    }

    @Test
    void acceptedResponseDatabaseFailureIsNotMisclassifiedAsAiFailure() {
        var prepared = prepared();
        var accepted = new AiGenerationAcceptedResponse("ai-301", AiGenerationStatus.PENDING);
        when(transactions.prepare(301L)).thenReturn(prepared);
        when(clients.getObject()).thenReturn(client);
        when(client.requestGeneration(request)).thenReturn(accepted);
        doThrow(new IllegalStateException("commit failed")).when(transactions).registerAccepted(prepared, accepted);

        assertThatThrownBy(() -> submissions.submit(301L)).hasMessage("commit failed");

        verify(transactions, never()).failSubmission(any(), any());
        verify(timers, never()).cancel(any());
        verifyNoInteractions(errors);
    }

    private GenerationCallbackTransactionService.Prepared prepared() {
        return new GenerationCallbackTransactionService.Prepared(
                301L, 0, Instant.now().plusSeconds(900), request, null, null);
    }
}
