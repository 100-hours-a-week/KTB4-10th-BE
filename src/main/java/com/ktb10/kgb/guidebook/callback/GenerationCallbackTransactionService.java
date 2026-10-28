package com.ktb10.kgb.guidebook.callback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.guidebook.client.AiClientException;
import com.ktb10.kgb.guidebook.client.AiFailureType;
import com.ktb10.kgb.guidebook.client.AiGenerationStatus;
import com.ktb10.kgb.guidebook.client.AiGuidebookRequestMapper;
import com.ktb10.kgb.guidebook.client.GuidebookAiClient;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRegenerationRequest;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import com.ktb10.kgb.guidebook.dto.GenerationFailurePayload;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.dto.request.RegenerationRequestPayload;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.entity.JobType;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import com.ktb10.kgb.guidebook.service.GuidebookResultService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** AI 접수·콜백·타임아웃의 상태 변경 트랜잭션입니다. */
@Service
public class GenerationCallbackTransactionService {

    private final GenerationJobRepository jobs;
    private final GuidebookResultService results;
    private final AiGuidebookRequestMapper mapper;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;
    private final GenerationCallbackSettings settings;
    private final Clock clock;
    private final Cache<String, Long> sequences;

    public GenerationCallbackTransactionService(
            GenerationJobRepository jobs,
            GuidebookResultService results,
            AiGuidebookRequestMapper mapper,
            ObjectMapper json,
            ApplicationEventPublisher events,
            GenerationCallbackSettings settings,
            Clock clock) {
        this.jobs = jobs;
        this.results = results;
        this.mapper = mapper;
        this.json = json;
        this.events = events;
        this.settings = settings;
        this.clock = clock;
        this.sequences = Caffeine.newBuilder().maximumSize(10_000)
                .expireAfterWrite(settings.timeout().multipliedBy(2)).build();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Submission submit(Long jobId, GuidebookAiClient client) {
        Prepared prepared = prepare(jobId);
        if (prepared == null) {
            return null;
        }
        try {
            AiGenerationAcceptedResponse accepted = prepared.initialRequest() != null
                    ? client.requestGeneration(prepared.initialRequest())
                    : client.requestRegeneration(
                            prepared.sourceAiJobId(), prepared.regenerationRequest());
            registerAccepted(prepared, accepted);
            return new Submission(prepared, null, null);
        } catch (AiClientException exception) {
            // 접수 응답만 유실된 경우 AI 콜백으로 복구될 수 있어 작업을 유지합니다.
            return new Submission(prepared, exception, "ai_callback_submission_uncertain");
        } catch (RuntimeException exception) {
            AiClientException failure = new AiClientException(
                    "AI 콜백 작업 접수에 실패했습니다.", exception,
                    AiFailureType.UNEXPECTED_ERROR, "/guidebooks-generations", null, 0L);
            failSubmission(prepared, failure);
            return new Submission(prepared, failure, "ai_callback_submission_failed");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Prepared prepare(Long jobId) {
        GenerationJob job = locked(jobId);
        if (!job.isPending() || job.getAiJobId() != null) {
            return null;
        }
        job.beginCallbackAttempt(LocalDateTime.now(clock));
        try {
            if (job.getJobType() == JobType.INITIAL) {
                InitialGenerationRequestPayload payload = json.readValue(
                        job.getRequestPayload(), InitialGenerationRequestPayload.class);
                return new Prepared(jobId, job.getAttemptCount(),
                        deadline(job), mapper.map(payload, jobId, job.getAttemptCount()),
                        null, null);
            }
            RegenerationRequestPayload payload = json.readValue(
                    job.getRequestPayload(), RegenerationRequestPayload.class);
            return new Prepared(jobId, job.getAttemptCount(), deadline(job), null,
                    payload.sourceAiJobId(),
                    new AiGuidebookRegenerationRequest(requestId(job), payload.request().feedback()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 AI 생성 요청을 읽을 수 없습니다.", exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerAccepted(Prepared prepared, AiGenerationAcceptedResponse accepted) {
        GenerationJob job = locked(prepared.jobId());
        if (!activeAttempt(job, prepared.attempt())) {
            return;
        }
        bindAiJob(job, accepted.jobId());
        if (Instant.now(clock).compareTo(prepared.deadline()) >= 0) {
            failTimeout(job);
        } else if (accepted.status() == AiGenerationStatus.PROCESSING && job.isPending()) {
            job.markProcessing(LocalDateTime.now(clock));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failSubmission(Prepared prepared, AiClientException exception) {
        GenerationJob job = locked(prepared.jobId());
        if (activeAttempt(job, prepared.attempt()) && job.getAiJobId() == null) {
            job.fail(serialize(GenerationFailurePayload.from(exception)), LocalDateTime.now(clock));
            events.publishEvent(GenerationUpdate.from(job, 0, null, "AI_SUBMISSION_FAILED"));
        }
    }

    @Transactional
    public void receive(GenerationCallbackEnvelope envelope) {
        GenerationCallbackEnvelope.Data data = envelope.data();
        GenerationJob job = locked(data.backendJobId());
        if (job.getAttemptCount() != data.attempt()) {
            return;
        }
        if (job.getAiJobId() != null && !job.getAiJobId().equals(data.jobId())) {
            throw new BusinessException(CommonErrorCode.RESOURCE_STATE_CONFLICT);
        }
        if (!activeAttempt(job, data.attempt())) {
            return;
        }
        if (job.getAttemptStartedAt() == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_STATE_CONFLICT);
        }
        if (!Instant.now(clock).isBefore(deadline(job))) {
            failTimeout(job);
            return;
        }
        if (data.status() == AiGenerationStatus.PENDING
                && job.getStatus() == GenerationStatus.PROCESSING) {
            return;
        }
        Long previous = sequences.getIfPresent(data.requestId());
        if (previous != null && data.sequence() <= previous
                && data.status() != AiGenerationStatus.COMPLETED
                && data.status() != AiGenerationStatus.FAILED) {
            return;
        }
        // request_id가 먼저 DB에 존재하므로 AI 202보다 빠른 콜백도 매핑할 수 있습니다.
        bindAiJob(job, data.jobId());
        results.apply(job.getId(), data.response());
        afterCommit(() -> sequences.asMap().merge(data.requestId(), data.sequence(), Math::max));
        String code = data.status() == AiGenerationStatus.FAILED ? data.error().code() : null;
        events.publishEvent(GenerationUpdate.from(job, data.sequence(), data.progress(), code));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expire(Long jobId, int attempt) {
        GenerationJob job = locked(jobId);
        if (activeAttempt(job, attempt) && job.getAttemptStartedAt() != null
                && !Instant.now(clock).isBefore(deadline(job))) {
            failTimeout(job);
        }
    }

    private void failTimeout(GenerationJob job) {
        job.fail(serialize(Map.of(
                "code", "GENERATION_TIMEOUT",
                "message", "가이드북 생성 제한 시간을 초과했습니다.",
                "retryable", true)), LocalDateTime.now(clock));
        events.publishEvent(GenerationUpdate.from(job, 0, null, "GENERATION_TIMEOUT"));
    }

    private GenerationJob locked(Long id) {
        return jobs.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    }

    private boolean activeAttempt(GenerationJob job, int attempt) {
        return job.getAttemptCount() == attempt
                && (job.getStatus() == GenerationStatus.PENDING
                || job.getStatus() == GenerationStatus.PROCESSING);
    }

    private void bindAiJob(GenerationJob job, String aiJobId) {
        if (job.getAiJobId() == null) {
            job.registerAiJob(aiJobId, LocalDateTime.now(clock));
        } else if (!job.getAiJobId().equals(aiJobId)) {
            throw new BusinessException(CommonErrorCode.RESOURCE_STATE_CONFLICT);
        }
    }

    private Instant deadline(GenerationJob job) {
        return job.getAttemptStartedAt().toInstant(ZoneOffset.UTC).plus(settings.timeout());
    }

    private String requestId(GenerationJob job) {
        return job.getId() + "-" + job.getAttemptCount();
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("생성 실패 정보를 저장할 수 없습니다.", exception);
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    public record Prepared(
            Long jobId, int attempt, Instant deadline,
            AiGuidebookRequest initialRequest, String sourceAiJobId,
            AiGuidebookRegenerationRequest regenerationRequest) {

        public String requestId() {
            return jobId + "-" + attempt;
        }
    }

    public record Submission(Prepared prepared, AiClientException failure, String failureEvent) {
    }
}
