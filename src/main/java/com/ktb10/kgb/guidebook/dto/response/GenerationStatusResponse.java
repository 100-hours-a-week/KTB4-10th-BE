package com.ktb10.kgb.guidebook.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;

public record GenerationStatusResponse(
        @JsonProperty("job_id")
        Long jobId,

        GenerationStatus status,

        @JsonProperty("guidebook_id")
        Long guidebookId,

        @JsonProperty("attempt_count")
        int attemptCount,

        GenerationError error) {

    public static GenerationStatusResponse from(
            GenerationJob job,
            GenerationError error) {
        return new GenerationStatusResponse(
                job.getId(),
                job.getStatus(),
                job.getGuidebookId(),
                job.getAttemptCount().intValue(),
                error);
    }

    /** 내부 AI 오류 원문 대신 클라이언트에 공개 가능한 오류만 전달합니다. */
    public record GenerationError(String code, String message) {
    }
}
