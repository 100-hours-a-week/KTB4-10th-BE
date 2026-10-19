package com.ktb10.kgb.guidebook.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.entity.JobType;

public record GuidebookRegenerationResponse(
        @JsonProperty("job_id")
        Long jobId,
        @JsonProperty("job_type")
        JobType jobType,
        GenerationStatus status,
        @JsonProperty("guidebook_id")
        Long guidebookId,
        @JsonProperty("current_version")
        Integer currentVersion) {

    public static GuidebookRegenerationResponse from(
            GenerationJob job,
            Integer currentVersion) {
        return new GuidebookRegenerationResponse(
                job.getId(),
                job.getJobType(),
                job.getStatus(),
                job.getGuidebookId(),
                currentVersion);
    }
}
