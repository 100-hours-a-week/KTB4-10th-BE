package com.ktb10.kgb.guidebook.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.guidebook.client.AiGenerationStatus;

/** AI 서버가 생성 작업을 접수한 결과입니다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiGenerationAcceptedResponse(
        @JsonProperty("job_id")
        String jobId,
        AiGenerationStatus status) {
}
