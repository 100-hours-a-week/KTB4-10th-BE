package com.ktb10.kgb.guidebook.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;

/** AI 원문·HTML을 제외한 생성 화면용 상태 이벤트입니다. */
public record GenerationUpdate(
        @JsonProperty("member_id") Long memberId,
        @JsonProperty("job_id") Long jobId,
        @JsonProperty("request_id") String requestId,
        long sequence,
        GenerationStatus status,
        GenerationCallbackEnvelope.Progress progress,
        @JsonProperty("guidebook_id") Long guidebookId,
        String code) {

    public static GenerationUpdate from(
            GenerationJob job,
            long sequence,
            GenerationCallbackEnvelope.Progress progress,
            String code) {
        return new GenerationUpdate(
                job.getMember().getId(), job.getId(),
                job.getId() + "-" + job.getAttemptCount(),
                sequence, job.getStatus(), progress, job.getGuidebookId(), code);
    }
}
