package com.ktb10.kgb.guidebook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.guidebook.client.AiClientException;

/** 생성 실패 복구에 필요한 최소 정보만 내부 JSON으로 저장합니다. */
public record GenerationFailurePayload(
        String code,
        @JsonProperty("failure_type")
        String failureType,
        boolean retryable) {

    private static final String SUBMISSION_FAILURE_CODE = "AI_SUBMISSION_FAILED";

    public static GenerationFailurePayload from(AiClientException exception) {
        return new GenerationFailurePayload(
                SUBMISSION_FAILURE_CODE,
                exception.getFailureType().code(),
                exception.getFailureType().isRetryable());
    }
}
