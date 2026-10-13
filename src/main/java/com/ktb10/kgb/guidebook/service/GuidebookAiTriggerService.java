package com.ktb10.kgb.guidebook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.guidebook.client.AiGenerationStatus;
import com.ktb10.kgb.guidebook.client.AiGuidebookRequestMapper;
import com.ktb10.kgb.guidebook.client.GuidebookAiClient;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 저장된 생성 입력을 AI 서버에 접수하고 외부 작업 ID를 연결합니다. */
@Service
@Profile({"local", "prod"})
public class GuidebookAiTriggerService {

    private final GenerationJobRepository generationJobRepository;
    private final GuidebookAiClient guidebookAiClient;
    private final AiGuidebookRequestMapper aiGuidebookRequestMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GuidebookAiTriggerService(
            GenerationJobRepository generationJobRepository,
            GuidebookAiClient guidebookAiClient,
            AiGuidebookRequestMapper aiGuidebookRequestMapper,
            ObjectMapper objectMapper,
            Clock clock) {
        this.generationJobRepository = generationJobRepository;
        this.guidebookAiClient = guidebookAiClient;
        this.aiGuidebookRequestMapper = aiGuidebookRequestMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trigger(Long jobId) {
        GenerationJob job = generationJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        if (!job.isPending() || job.getAiJobId() != null) {
            return;
        }

        InitialGenerationRequestPayload payload = deserialize(job.getRequestPayload());
        AiGuidebookRequest request = aiGuidebookRequestMapper.map(
                payload, job.getId(), job.getAttemptCount());
        AiGenerationAcceptedResponse response = guidebookAiClient.requestGeneration(request);
        LocalDateTime now = LocalDateTime.now(clock);
        job.registerAiJob(response.jobId(), now);
        if (response.status() == AiGenerationStatus.PROCESSING) {
            job.markProcessing(now);
        }
    }

    private InitialGenerationRequestPayload deserialize(String requestPayload) {
        try {
            return objectMapper.readValue(requestPayload, InitialGenerationRequestPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 가이드북 생성 요청을 읽을 수 없습니다.", exception);
        }
    }
}
