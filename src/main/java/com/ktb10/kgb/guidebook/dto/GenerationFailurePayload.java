package com.ktb10.kgb.guidebook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.guidebook.client.AiClientException;

/** 생성 실패 복구에 필요한 최소 정보만 내부 JSON으로 저장합니다. */
public record GenerationFailurePayload(
        String code,
        @JsonProperty("failure_type")
        String failureType,
        boolean retryable,
        String message) {

    private static final String SUBMISSION_FAILURE_CODE = "AI_SUBMISSION_FAILED";

    public static GenerationFailurePayload from(AiClientException exception) {
        String inputMessage = GenerationFailurePolicy.inputMessage(exception.getUpstreamCode());
        if (inputMessage != null) {
            return new GenerationFailurePayload(exception.getUpstreamCode(),
                    exception.getFailureType().code(), false, inputMessage);
        }
        if (Integer.valueOf(429).equals(exception.getUpstreamStatus())) {
            return new GenerationFailurePayload("AI_RATE_LIMITED", exception.getFailureType().code(),
                    true, "요청이 몰려 AI가 작업을 접수하지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
        return new GenerationFailurePayload(
                SUBMISSION_FAILURE_CODE,
                exception.getFailureType().code(),
                exception.getFailureType().isRetryable(),
                "AI 서버가 작업을 접수하지 못했어요. 잠시 후 다시 이용해 주세요.");
    }
}
