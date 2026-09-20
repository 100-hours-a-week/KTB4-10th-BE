package com.ktb10.kgb.guidebook.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.entity.JobType;

public record GuidebookGenerationResponse(
        @JsonProperty("job_id")
        Long jobId,

        @JsonProperty("job_type")
        JobType jobType,

        GenerationStatus status,

        @JsonProperty("guidebook_id")
        Long guidebookId) {

    public static GuidebookGenerationResponse from(GenerationJob job) {
        return new GuidebookGenerationResponse(
                job.getId(),
                job.getJobType(),
                job.getStatus(),
                job.getGuidebookId());
    }
}
