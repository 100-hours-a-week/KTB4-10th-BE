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
    void onlyCurrentClaimCanConfirmPublication() {
        GenerationJob job = initialJob();
        job.preparePublication("{}", NOW);
        job.claimPublication("old", NOW.plusSeconds(60));
        job.claimPublication("current", NOW.plusSeconds(120));
        job.finishPublication(0, "old", null, NOW.plusSeconds(61));
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PUBLISH_PENDING);
        job.finishPublication(0, "current", "MQ_CONFIRM_TIMEOUT", NOW.plusSeconds(62));
        assertThat(job.getNextPublishAt()).isAfter(NOW.plusSeconds(62));
        assertThat(job.getPublishClaimToken()).isNull();
        job.claimPublication("retry", NOW.plusSeconds(180));
        job.finishPublication(0, "retry", null, NOW.plusSeconds(70));
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.QUEUED);
        assertThat(job.getAttemptCount()).isZero();
    }

    @Test
    void existingHttpJobsHaveNoOutboxMessageOrClaim() {
        GenerationJob initial = initialJob();
        GenerationJob regeneration = GenerationJob.createRegeneration(
                member(), 50L, "{}", "regeneration-key", NOW);
        for (GenerationJob job : java.util.List.of(initial, regeneration)) {
            assertThatThrownBy(job::getMessageId).isInstanceOf(IllegalStateException.class);
            assertThat(job.getMessagePayload()).isNull();
            assertThat(job.getPublishAttemptCount()).isZero();
            assertThat(job.getNextPublishAt()).isNull();
            assertThat(job.getPublishClaimToken()).isNull();
            assertThat(job.getPublishClaimExpiresAt()).isNull();
            assertThat(job.getPublishedAt()).isNull();
            assertThat(job.getLastPublishErrorCode()).isNull();
        }
    }

    @Test
    void messageIdentityUsesBackendJobAndUserAttemptAndRetryClearsPublication() {
        GenerationJob job = initialJob();
        org.springframework.test.util.ReflectionTestUtils.setField(job, "id", 123L);
        org.springframework.test.util.ReflectionTestUtils.setField(job, "messagePayload", "{}");
        org.springframework.test.util.ReflectionTestUtils.setField(job, "publishAttemptCount", 2);
        org.springframework.test.util.ReflectionTestUtils.setField(job, "publishClaimToken", "old-claim");
        org.springframework.test.util.ReflectionTestUtils.setField(job, "publishedAt", NOW);
        assertThat(job.getMessageId()).isEqualTo("123-0");
        job.fail("{\"code\":\"timeout\"}", NOW);
        job.retry(NOW.plusSeconds(1));
        assertThat(job.getMessageId()).isEqualTo("123-1");
        assertThat(job.getMessagePayload()).isNull();
        assertThat(job.getPublishAttemptCount()).isZero();
        assertThat(job.getPublishClaimToken()).isNull();
        assertThat(job.getPublishedAt()).isNull();
    }

    @Test
    void queuesMqJobAndDoesNotRegressAfterProcessing() {
        GenerationJob job = initialJob();
        job.markPublishPending(NOW);
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PUBLISH_PENDING);
        job.markQueued(NOW.plusSeconds(1));
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.QUEUED);
        job.markProcessing(NOW.plusSeconds(2));
        job.markQueued(NOW.plusSeconds(3));
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PROCESSING);
        job.complete(50L, NOW.plusMinutes(1));
        job.markQueued(NOW.plusMinutes(2));
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
    }

    @Test
    void acceptsProcessingBeforePublishConfirmation() {
        GenerationJob job = initialJob();
        job.markPublishPending(NOW);
        job.markProcessing(NOW.plusSeconds(1));
        job.markQueued(NOW.plusSeconds(2));
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PROCESSING);
    }

    @Test
    void mqWaitingJobsCanFailOrBeCanceled() {
        for (boolean queued : new boolean[]{false, true}) {
            GenerationJob failed = initialJob();
            failed.markPublishPending(NOW);
            if (queued) {
                failed.markQueued(NOW);
            }
            failed.fail("{\"code\":\"timeout\"}", NOW);
            assertThat(failed.getStatus()).isEqualTo(GenerationStatus.FAILED);
            failed.retry(NOW.plusSeconds(1));
            failed.markPublishPending(NOW.plusSeconds(1));
            assertThat(failed.getStatus()).isEqualTo(GenerationStatus.PUBLISH_PENDING);

            GenerationJob canceled = initialJob();
            canceled.markPublishPending(NOW);
            if (queued) {
                canceled.markQueued(NOW);
            }
            assertThat(canceled.cancelForWithdrawal(NOW)).isTrue();
            canceled.markQueued(NOW.plusSeconds(1));
            assertThat(canceled.getStatus()).isEqualTo(GenerationStatus.CANCELED);
        }
    }

    @Test
    void cannotPublishAnAlreadyStartedHttpAttempt() {
        GenerationJob job = initialJob();
        job.beginCallbackAttempt(NOW);
        assertThatThrownBy(() -> job.markPublishPending(NOW))
                .isInstanceOf(IllegalStateException.class);
    }

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
    void createsPendingRegenerationJobLinkedToGuidebook() {
        GenerationJob job = GenerationJob.createRegeneration(
                member(),
                50L,
                "{\"feedback\":\"여유롭게\"}",
                "regeneration-key",
                NOW);

        assertThat(job.getJobType()).isEqualTo(JobType.REGENERATION);
        assertThat(job.getGuidebookId()).isEqualTo(50L);
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PENDING);
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
                member(),
                "{\"regionId\":1}",
                "initial-key",
                NOW);
    }

    private static Member member() {
        return Member.register(
                OauthProvider.KAKAO,
                "generation-job-member",
                "여행자",
                null,
                null,
                NOW);
    }
}
