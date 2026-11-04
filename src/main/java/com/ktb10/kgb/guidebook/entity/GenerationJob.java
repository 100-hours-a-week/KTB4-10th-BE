package com.ktb10.kgb.guidebook.entity;

import com.ktb10.kgb.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "generation_jobs",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_generation_jobs_member_idempotency",
                    columnNames = {"member_id", "idempotency_key"}),
            @UniqueConstraint(
                    name = "uq_generation_jobs_active_member",
                    columnNames = "active_member_id"),
            @UniqueConstraint(
                    name = "uq_generation_jobs_ai_job_id",
                    columnNames = "ai_job_id")
        },
        indexes = {
            @Index(
                    name = "ix_generation_jobs_member_state",
                    columnList = "member_id, status, created_at"),
            @Index(
                    name = "ix_generation_jobs_guidebook_created",
                    columnList = "guidebook_id, created_at"),
            @Index(
                    name = "ix_generation_jobs_publish_due",
                    columnList = "status, next_publish_at, id"),
            @Index(
                    name = "ix_generation_jobs_publish_claim",
                    columnList = "status, publish_claim_expires_at"),
            @Index(
                    name = "ix_generation_jobs_lease",
                    columnList = "status, lease_expires_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GenerationJob {

    private static final int MAX_RETRY_COUNT = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_generation_jobs_member"))
    private Member member;

    @Column(name = "guidebook_id")
    private Long guidebookId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", length = 20, nullable = false)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private GenerationStatus status;

    @Column(name = "request_payload", nullable = false, columnDefinition = "json")
    private String requestPayload;

    /** AI에 전달할 전체 입력을 포함한 변경 불가능한 메시지 스냅샷입니다. */
    @Column(name = "message_payload", columnDefinition = "json")
    private String messagePayload;

    @Column(name = "publish_attempt_count", nullable = false)
    private int publishAttemptCount;

    @Column(name = "next_publish_at")
    private LocalDateTime nextPublishAt;

    @Column(name = "publish_claim_token", length = 36)
    private String publishClaimToken;

    @Column(name = "publish_claim_expires_at")
    private LocalDateTime publishClaimExpiresAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "last_publish_error_code", length = 100)
    private String lastPublishErrorCode;

    @Column(name = "error_payload", columnDefinition = "json")
    private String errorPayload;

    @Column(name = "attempt_count", nullable = false)
    private Short attemptCount;

    @Column(name = "idempotency_key", length = 100, nullable = false)
    private String idempotencyKey;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "attempt_started_at")
    private LocalDateTime attemptStartedAt;

    @Column(name = "lease_token", length = 36)
    private String leaseToken;

    @Column(name = "lease_version", nullable = false)
    private Long leaseVersion;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "ai_job_id", length = 255)
    private String aiJobId;

    @Column(name = "cancel_requested_at")
    private LocalDateTime cancelRequestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "active_member_id", insertable = false, updatable = false)
    private Long activeMemberId;

    /** 동일 시도의 재발행에는 고정되고 사용자 재시도마다 달라지는 메시지 식별자입니다. */
    public String getMessageId() {
        if (id == null) {
            throw new IllegalStateException("메시지 ID는 작업 저장 후 생성할 수 있습니다.");
        }
        return id + "-" + attemptCount;
    }

    public static GenerationJob createInitial(
            Member member,
            String requestPayload,
            String idempotencyKey,
            LocalDateTime createdAt) {
        GenerationJob job = new GenerationJob();
        job.member = member;
        job.jobType = JobType.INITIAL;
        job.status = GenerationStatus.PENDING;
        job.requestPayload = requestPayload;
        job.attemptCount = (short) 0;
        job.idempotencyKey = idempotencyKey;
        job.leaseVersion = 0L;
        job.createdAt = createdAt;
        job.updatedAt = createdAt;
        return job;
    }

    public static GenerationJob createRegeneration(
            Member member,
            Long guidebookId,
            String requestPayload,
            String idempotencyKey,
            LocalDateTime createdAt) {
        GenerationJob job = new GenerationJob();
        job.member = Objects.requireNonNull(member);
        job.guidebookId = Objects.requireNonNull(guidebookId);
        job.jobType = JobType.REGENERATION;
        job.status = GenerationStatus.PENDING;
        job.requestPayload = Objects.requireNonNull(requestPayload);
        job.attemptCount = (short) 0;
        job.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        job.leaseVersion = 0L;
        job.createdAt = Objects.requireNonNull(createdAt);
        job.updatedAt = createdAt;
        return job;
    }

    public void registerAiJob(String aiJobId, LocalDateTime registeredAt) {
        if (status != GenerationStatus.PENDING) {
            return;
        }
        if (this.aiJobId != null) {
            throw new IllegalStateException("AI 작업 ID가 이미 등록되어 있습니다.");
        }
        if (aiJobId == null || aiJobId.isBlank()) {
            throw new IllegalArgumentException("AI 작업 ID는 비어 있을 수 없습니다.");
        }
        this.aiJobId = aiJobId;
        this.updatedAt = registeredAt;
    }

    public boolean isPending() {
        return status == GenerationStatus.PENDING;
    }

    public void preparePublication(String payload, LocalDateTime now) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("발행 메시지는 비어 있을 수 없습니다.");
        }
        if (messagePayload != null) {
            throw new IllegalStateException("이미 준비된 메시지는 변경할 수 없습니다.");
        }
        Objects.requireNonNull(now);
        markPublishPending(now);
        messagePayload = payload;
        nextPublishAt = now;
    }

    /** 기존 HTTP 접수와 구분되는 MQ 발행 대기 상태로 전환합니다. */
    public void markPublishPending(LocalDateTime now) {
        if (status == GenerationStatus.PUBLISH_PENDING) {
            return;
        }
        if (status != GenerationStatus.PENDING || attemptStartedAt != null) {
            throw new IllegalStateException("접수 전 대기 작업만 MQ 발행 대기로 변경할 수 있습니다.");
        }
        updatedAt = Objects.requireNonNull(now);
        status = GenerationStatus.PUBLISH_PENDING;
    }

    /** 늦은 발행 확인은 실행 중 또는 종료된 작업의 상태를 되돌리지 않습니다. */
    public void markQueued(LocalDateTime now) {
        if (status != GenerationStatus.PUBLISH_PENDING) {
            return;
        }
        updatedAt = Objects.requireNonNull(now);
        status = GenerationStatus.QUEUED;
    }

    public void claimPublication(String token, LocalDateTime expiresAt) {
        publishClaimToken = Objects.requireNonNull(token);
        publishClaimExpiresAt = Objects.requireNonNull(expiresAt);
        publishAttemptCount++;
    }

    public void finishPublication(int attempt, String token, String error, LocalDateTime now) {
        if (status != GenerationStatus.PUBLISH_PENDING || attemptCount != attempt
                || !Objects.equals(publishClaimToken, token)) {
            return;
        }
        publishClaimToken = null;
        publishClaimExpiresAt = null;
        lastPublishErrorCode = error;
        if (error == null) {
            publishedAt = now;
            nextPublishAt = null;
            markQueued(now);
        } else {
            nextPublishAt = now.plusSeconds(Math.min(60, 1L << Math.min(6, publishAttemptCount)));
            updatedAt = now;
        }
    }

    /** 콜백 방식의 전체 제한 시간은 큐 대기 전 접수 시도부터 계산합니다. */
    public void beginCallbackAttempt(LocalDateTime now) {
        if (status == GenerationStatus.PENDING && attemptStartedAt == null) {
            attemptStartedAt = Objects.requireNonNull(now);
        }
    }

    public boolean cancelForWithdrawal(LocalDateTime canceledAt) {
        if (!status.isActive()) {
            return false;
        }
        LocalDateTime now = java.util.Objects.requireNonNull(
                canceledAt, "취소 시각은 null일 수 없습니다.");
        status = GenerationStatus.CANCELED;
        cancelRequestedAt = now;
        completedAt = now;
        leaseToken = null;
        leaseExpiresAt = null;
        leaseVersion = Math.addExact(leaseVersion, 1L);
        updatedAt = now;
        return true;
    }

    public void markProcessing(LocalDateTime processedAt) {
        if (status == GenerationStatus.PROCESSING) {
            return;
        }
        if (!status.isWaiting()) {
            throw new IllegalStateException("AI 작업을 처리 중으로 변경할 수 없는 상태입니다: " + status);
        }
        status = GenerationStatus.PROCESSING;
        startedAt = processedAt;
        if (attemptStartedAt == null) {
            attemptStartedAt = processedAt;
        }
        updatedAt = processedAt;
    }

    public void complete(Long completedGuidebookId, LocalDateTime completedAt) {
        if (status == GenerationStatus.COMPLETED) {
            return;
        }
        if (!status.isActive()) {
            throw new IllegalStateException("완료할 수 없는 생성 작업 상태입니다: " + status);
        }
        guidebookId = Objects.requireNonNull(completedGuidebookId);
        status = GenerationStatus.COMPLETED;
        this.completedAt = Objects.requireNonNull(completedAt);
        updatedAt = completedAt;
    }

    public void fail(String failurePayload, LocalDateTime failedAt) {
        if (!status.isActive()) {
            throw new IllegalStateException("실패할 수 없는 생성 작업 상태입니다: " + status);
        }
        if (failurePayload == null || failurePayload.isBlank()) {
            throw new IllegalArgumentException("실패 정보는 비어 있을 수 없습니다.");
        }
        status = GenerationStatus.FAILED;
        errorPayload = failurePayload;
        completedAt = Objects.requireNonNull(failedAt);
        updatedAt = failedAt;
    }

    public void retry(LocalDateTime retriedAt) {
        if (status != GenerationStatus.FAILED) {
            throw new IllegalStateException("실패한 생성 작업만 다시 시도할 수 있습니다.");
        }
        if (attemptCount >= MAX_RETRY_COUNT) {
            throw new IllegalStateException("가이드북 생성 재시도 횟수를 초과했습니다.");
        }
        attemptCount++;
        status = GenerationStatus.PENDING;
        aiJobId = null;
        errorPayload = null;
        startedAt = null;
        attemptStartedAt = null;
        messagePayload = null;
        publishAttemptCount = 0;
        nextPublishAt = null;
        publishClaimToken = null;
        publishClaimExpiresAt = null;
        publishedAt = null;
        lastPublishErrorCode = null;
        completedAt = null;
        updatedAt = Objects.requireNonNull(retriedAt);
    }
}
