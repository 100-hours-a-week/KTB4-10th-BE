package com.ktb10.kgb.guidebook.service;

import com.ktb10.kgb.guidebook.client.AiClientException;
import com.ktb10.kgb.guidebook.client.AiIntegrationErrorLogger;
import com.ktb10.kgb.guidebook.client.GuidebookAiClient;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** AI 작업 상태를 조회해 저장된 생성 작업에 반영합니다. */
@Service
@ConditionalOnProperty(name = "ai.generation.delivery-mode", havingValue = "polling", matchIfMissing = true)
public class GuidebookGenerationStatusSynchronizer {

    private static final Set<GenerationStatus> ACTIVE_STATUSES = EnumSet.of(
            GenerationStatus.PENDING,
            GenerationStatus.PROCESSING);

    private final GenerationJobRepository generationJobRepository;
    private final ObjectProvider<GuidebookAiClient> guidebookAiClientProvider;
    private final GuidebookResultService guidebookResultService;
    private final AiIntegrationErrorLogger errorLogger;

    public GuidebookGenerationStatusSynchronizer(
            GenerationJobRepository generationJobRepository,
            ObjectProvider<GuidebookAiClient> guidebookAiClientProvider,
            GuidebookResultService guidebookResultService,
            AiIntegrationErrorLogger errorLogger) {
        this.generationJobRepository = generationJobRepository;
        this.guidebookAiClientProvider = guidebookAiClientProvider;
        this.guidebookResultService = guidebookResultService;
        this.errorLogger = errorLogger;
    }

    public void synchronize(Long jobId) {
        GenerationJob job = generationJobRepository.findById(jobId).orElse(null);
        if (job == null
                || !ACTIVE_STATUSES.contains(job.getStatus())
                || job.getAiJobId() == null) {
            return;
        }
        GuidebookAiClient aiClient = guidebookAiClientProvider.getIfAvailable();
        if (aiClient == null) {
            return;
        }
        try {
            AiGenerationStatusResponse response = aiClient.getGenerationStatus(job.getAiJobId());
            guidebookResultService.apply(jobId, response);
        } catch (AiClientException exception) {
            errorLogger.logFailure(
                    "ai_generation_scheduled_status_sync_failed",
                    job.getId(),
                    job.getAiJobId(),
                    exception);
        }
    }
}
