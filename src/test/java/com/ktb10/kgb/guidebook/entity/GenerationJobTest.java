package com.ktb10.kgb.guidebook.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
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
        assertThat(job.getMember()).isNotNull();
    }

    private static GenerationJob initialJob() {
        return GenerationJob.createInitial(
                Member.register(
                        OauthProvider.KAKAO,
                        "generation-job-member",
                        "여행자",
                        null,
                        null,
                        NOW),
                "{\"regionId\":1}",
                "initial-key",
                NOW);
    }
}
