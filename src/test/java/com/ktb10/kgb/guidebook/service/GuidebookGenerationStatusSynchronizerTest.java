package com.ktb10.kgb.guidebook.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.guidebook.client.AiClientException;
import com.ktb10.kgb.guidebook.client.AiFailureType;
import com.ktb10.kgb.guidebook.client.AiIntegrationErrorLogger;
import com.ktb10.kgb.guidebook.client.GuidebookAiClient;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class GuidebookGenerationStatusSynchronizerTest {

    @Mock
    private GenerationJobRepository generationJobRepository;
    @Mock
    private ObjectProvider<GuidebookAiClient> guidebookAiClientProvider;
    @Mock
    private GuidebookAiClient guidebookAiClient;
    @Mock
    private GuidebookResultService guidebookResultService;
    @Mock
    private AiIntegrationErrorLogger errorLogger;
    @Mock
    private GenerationJob job;
    private GuidebookGenerationStatusSynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        synchronizer = new GuidebookGenerationStatusSynchronizer(
                generationJobRepository,
                guidebookAiClientProvider,
                guidebookResultService,
                errorLogger);
    }

    @Test
    void appliesAiStatusForActiveJob() {
        AiGenerationStatusResponse response = AiGenerationStatusResponse.processing("ai-31");
        given(generationJobRepository.findById(31L)).willReturn(Optional.of(job));
        given(job.getStatus()).willReturn(GenerationStatus.PROCESSING);
        given(job.getAiJobId()).willReturn("ai-31");
        given(guidebookAiClientProvider.getIfAvailable()).willReturn(guidebookAiClient);
        given(guidebookAiClient.getGenerationStatus("ai-31")).willReturn(response);

        synchronizer.synchronize(31L);

        verify(guidebookResultService).apply(31L, response);
    }

    @Test
    void leavesJobActiveWhenAiStatusRequestTemporarilyFails() {
        AiClientException exception = new AiClientException(
                "timeout", null, AiFailureType.READ_TIMEOUT, "/jobs/ai-31", null, 10_000L);
        given(generationJobRepository.findById(31L)).willReturn(Optional.of(job));
        given(job.getId()).willReturn(31L);
        given(job.getStatus()).willReturn(GenerationStatus.PROCESSING);
        given(job.getAiJobId()).willReturn("ai-31");
        given(guidebookAiClientProvider.getIfAvailable()).willReturn(guidebookAiClient);
        given(guidebookAiClient.getGenerationStatus("ai-31")).willThrow(exception);

        synchronizer.synchronize(31L);

        verify(guidebookResultService, never()).apply(31L, null);
        verify(errorLogger).logFailure(
                "ai_generation_scheduled_status_sync_failed", 31L, "ai-31", exception);
    }
}
