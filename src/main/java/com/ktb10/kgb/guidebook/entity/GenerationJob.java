package com.ktb10.kgb.guidebook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
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
                    columnNames = "active_member_id")
        },
        indexes = {
            @Index(
                    name = "ix_generation_jobs_member_state",
                    columnList = "member_id, status, created_at"),
            @Index(
                    name = "ix_generation_jobs_guidebook_created",
                    columnList = "guidebook_id, created_at"),
            @Index(
                    name = "ix_generation_jobs_next_attempt",
                    columnList = "status, next_attempt_at"),
            @Index(
                    name = "ix_generation_jobs_lease",
                    columnList = "status, lease_expires_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

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

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

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

    public static GenerationJob createInitial(
            Long memberId,
            String requestPayload,
            String idempotencyKey,
            LocalDateTime createdAt) {
        GenerationJob job = new GenerationJob();
        job.memberId = memberId;
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
}
