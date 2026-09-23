package com.ktb10.kgb.guidebook.client;

import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;

/** 가이드북 생성 도메인이 사용하는 AI 서버 통신 경계입니다. */
public interface GuidebookAiClient {

    AiGenerationAcceptedResponse requestGeneration(AiGuidebookRequest request);

    AiGenerationStatusResponse getGenerationStatus(String aiJobId);

    AiGenerationAcceptedResponse retryGeneration(String aiJobId);
}
