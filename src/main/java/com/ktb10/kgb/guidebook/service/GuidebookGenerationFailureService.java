package com.ktb10.kgb.guidebook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.guidebook.client.AiClientException;
import com.ktb10.kgb.guidebook.dto.GenerationFailurePayload;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 커밋 이후 AI 최초 접수 실패를 별도 트랜잭션에서 종료 상태로 복구합니다. */
@Service
public class GuidebookGenerationFailureService {

    private final GenerationJobRepository generationJobRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GuidebookGenerationFailureService(
            GenerationJobRepository generationJobRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.generationJobRepository = generationJobRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failInitialSubmission(Long jobId, AiClientException exception) {
        GenerationJob job = generationJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        if (!job.isPending() || job.getAiJobId() != null) {
            return;
        }

        job.fail(serialize(GenerationFailurePayload.from(exception)), LocalDateTime.now(clock));
    }

    private String serialize(GenerationFailurePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("가이드북 생성 실패 정보를 저장할 수 없습니다.", exception);
        }
    }
}
