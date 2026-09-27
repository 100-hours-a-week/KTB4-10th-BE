package com.ktb10.kgb.guidebook.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void registersExternalAiJobIdOnce() {
        GenerationJob job = initialJob();
        LocalDateTime registeredAt = NOW.plusMinutes(1);

        job.registerAiJob("job_12345", registeredAt);

        assertThat(job.getAiJobId()).isEqualTo("job_12345");
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PENDING);
        assertThat(job.getStartedAt()).isNull();
        assertThat(job.getAttemptStartedAt()).isNull();
        assertThat(job.getUpdatedAt()).isEqualTo(registeredAt);
        assertThatThrownBy(() -> job.registerAiJob("job_67890", registeredAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsBlankAiJobId() {
        GenerationJob job = initialJob();

        assertThatThrownBy(() -> job.registerAiJob(" ", NOW.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancelsPendingJobForMemberWithdrawal() {
        GenerationJob job = initialJob();
        LocalDateTime canceledAt = NOW.plusMinutes(2);

        boolean canceled = job.cancelForWithdrawal(canceledAt);

        assertThat(canceled).isTrue();
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.CANCELED);
        assertThat(job.getCancelRequestedAt()).isEqualTo(canceledAt);
        assertThat(job.getCompletedAt()).isEqualTo(canceledAt);
        assertThat(job.getLeaseVersion()).isOne();
        assertThat(job.getUpdatedAt()).isEqualTo(canceledAt);
    }

    @Test
    void canceledJobIgnoresLateAiRegistration() {
        GenerationJob job = initialJob();
        job.cancelForWithdrawal(NOW.plusMinutes(1));

        job.registerAiJob("late_job", NOW.plusMinutes(2));

        assertThat(job.getStatus()).isEqualTo(GenerationStatus.CANCELED);
        assertThat(job.getAiJobId()).isNull();
    }

    @Test
    void retriesFailedJobWithFreshAiState() {
        GenerationJob job = initialJob();
        job.registerAiJob("job_12345", NOW.plusMinutes(1));
        job.markProcessing(NOW.plusMinutes(2));
        job.fail("{\"code\":\"AI_TIMEOUT\"}", NOW.plusMinutes(3));

        job.retry(NOW.plusMinutes(4));

        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PENDING);
        assertThat(job.getAttemptCount()).isEqualTo((short) 1);
        assertThat(job.getAiJobId()).isNull();
        assertThat(job.getErrorPayload()).isNull();
        assertThat(job.getStartedAt()).isNull();
        assertThat(job.getAttemptStartedAt()).isNull();
        assertThat(job.getCompletedAt()).isNull();
    }

    @Test
    void allowsAtMostThreeRetries() {
        GenerationJob job = initialJob();

        for (int attempt = 0; attempt < 3; attempt++) {
            job.fail("{\"code\":\"AI_TIMEOUT\"}", NOW.plusMinutes(attempt * 2L + 1));
            job.retry(NOW.plusMinutes(attempt * 2L + 2));
        }
        job.fail("{\"code\":\"AI_TIMEOUT\"}", NOW.plusMinutes(7));

        assertThatThrownBy(() -> job.retry(NOW.plusMinutes(8)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재시도 횟수");
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
