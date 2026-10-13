package com.ktb10.kgb.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.guidebook.client.AiClientException;
import com.ktb10.kgb.guidebook.client.AiFailureType;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuidebookGenerationFailureServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-04T03:00:00Z"), ZoneOffset.UTC);

    @Mock
    private GenerationJobRepository generationJobRepository;

    private ObjectMapper objectMapper;
    private GuidebookGenerationFailureService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new GuidebookGenerationFailureService(
                generationJobRepository, objectMapper, CLOCK);
    }

    @Test
    void marksPendingJobFailedWithSanitizedRetryablePayload() throws Exception {
        GenerationJob job = pendingJob();
        given(generationJobRepository.findByIdForUpdate(301L)).willReturn(Optional.of(job));

        service.failInitialSubmission(301L, failure(AiFailureType.CONNECT_TIMEOUT));

        assertThat(job.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(job.getCompletedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        JsonNode payload = objectMapper.readTree(job.getErrorPayload());
        assertThat(payload.get("code").asText()).isEqualTo("AI_SUBMISSION_FAILED");
        assertThat(payload.get("failure_type").asText()).isEqualTo("connect_timeout");
        assertThat(payload.get("retryable").asBoolean()).isTrue();
        assertThat(job.getErrorPayload()).doesNotContain("private upstream response");
    }

    @Test
    void recordsUpstreamClientErrorAsNotRetryable() throws Exception {
        GenerationJob job = pendingJob();
        given(generationJobRepository.findByIdForUpdate(301L)).willReturn(Optional.of(job));

        service.failInitialSubmission(301L, failure(AiFailureType.UPSTREAM_4XX));

        JsonNode payload = objectMapper.readTree(job.getErrorPayload());
        assertThat(payload.get("retryable").asBoolean()).isFalse();
    }

    @Test
    void recordsUpstreamServerErrorAsRetryable() throws Exception {
        GenerationJob job = pendingJob();
        given(generationJobRepository.findByIdForUpdate(301L)).willReturn(Optional.of(job));

        service.failInitialSubmission(301L, failure(AiFailureType.UPSTREAM_5XX));

        JsonNode payload = objectMapper.readTree(job.getErrorPayload());
        assertThat(payload.get("failure_type").asText()).isEqualTo("upstream_5xx");
        assertThat(payload.get("retryable").asBoolean()).isTrue();
    }

    @Test
    void doesNotOverwriteCanceledJob() {
        GenerationJob job = pendingJob();
        job.cancelForWithdrawal(LocalDateTime.now(CLOCK));
        given(generationJobRepository.findByIdForUpdate(301L)).willReturn(Optional.of(job));

        service.failInitialSubmission(301L, failure(AiFailureType.UPSTREAM_5XX));

        assertThat(job.getStatus()).isEqualTo(GenerationStatus.CANCELED);
        assertThat(job.getErrorPayload()).isNull();
    }

    @Test
    void doesNotOverwriteJobAfterAiJobWasRegistered() {
        GenerationJob job = pendingJob();
        job.registerAiJob("ai-job-301", LocalDateTime.now(CLOCK));
        given(generationJobRepository.findByIdForUpdate(301L)).willReturn(Optional.of(job));

        service.failInitialSubmission(301L, failure(AiFailureType.UPSTREAM_5XX));

        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PENDING);
        assertThat(job.getErrorPayload()).isNull();
    }

    private GenerationJob pendingJob() {
        Member member = Member.register(
                OauthProvider.KAKAO,
                "submission-failure-member",
                "여행자",
                null,
                null,
                LocalDateTime.now(CLOCK));
        return GenerationJob.createInitial(
                member, "{}", "submission-key", LocalDateTime.now(CLOCK));
    }

    private AiClientException failure(AiFailureType failureType) {
        return new AiClientException(
                "AI 접수 실패",
                new IllegalStateException("private upstream response"),
                failureType,
                "/guidebooks-generations",
                503,
                100L);
    }
}
