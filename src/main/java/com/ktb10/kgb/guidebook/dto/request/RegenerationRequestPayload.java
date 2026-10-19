package com.ktb10.kgb.guidebook.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 멱등 비교와 재생성 결과 검증에 필요한 요청 스냅샷입니다. */
public record RegenerationRequestPayload(
        GuidebookRegenerationRequest request,
        @JsonProperty("source_request")
        InitialGenerationRequestPayload sourceRequest,
        @JsonProperty("source_ai_job_id")
        String sourceAiJobId) {
}
