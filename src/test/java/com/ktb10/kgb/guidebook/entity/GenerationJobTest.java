package com.ktb10.kgb.guidebook.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GenerationJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 12, 0);

    @Test
    void createsPendingInitialJob() {
        GenerationJob job = initialJob();

        assertThat(job.getJobType()).isEqualTo(JobType.INITIAL);
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PENDING);
        assertThat(job.getGuidebookId()).isNull();
        assertThat(job.getAttemptCount()).isZero();
        assertThat(job.getLeaseVersion()).isZero();
    }

    private static GenerationJob initialJob() {
        return GenerationJob.createInitial(
                1L,
                "{\"regionId\":1}",
                "initial-key",
                NOW);
    }
}
