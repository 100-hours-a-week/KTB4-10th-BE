package com.ktb10.kgb.guidebook.event;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.guidebook.client.AiClientException;
import com.ktb10.kgb.guidebook.client.AiFailureType;
import com.ktb10.kgb.guidebook.client.AiIntegrationErrorLogger;
import com.ktb10.kgb.guidebook.service.GuidebookAiTriggerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuidebookGenerationEventListenerTest {

    @Mock
    private GuidebookAiTriggerService guidebookAiTriggerService;

    @Mock
    private AiIntegrationErrorLogger errorLogger;

    @Test
    void triggersAiGenerationForCommittedJob() {
        GuidebookGenerationEventListener listener =
                new GuidebookGenerationEventListener(guidebookAiTriggerService, errorLogger);

        listener.handle(new GuidebookGenerationRequestedEvent(301L));

        verify(guidebookAiTriggerService).trigger(301L);
    }

    @Test
    void logsClassifiedFailureOnceAtListenerBoundary() {
        GuidebookGenerationEventListener listener =
                new GuidebookGenerationEventListener(guidebookAiTriggerService, errorLogger);
        AiClientException exception = new AiClientException(
                "AI 서버 요청에 실패했습니다.",
                new IllegalStateException("connection reset"),
                AiFailureType.CONNECTION_ERROR,
                "/guidebooks-generations",
                null,
                12L);
        willThrow(exception).given(guidebookAiTriggerService).trigger(301L);

        listener.handle(new GuidebookGenerationRequestedEvent(301L));

        verify(errorLogger).logFailure(
                "ai_generation_request_failed",
                301L,
                null,
                exception);
    }
}
