package com.ktb10.kgb.member.entity;

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

/** 알림과 함께 저장되어 서버 재시작 뒤에도 남는 Web Push 발송 작업입니다. */
@Entity
@Table(
        name = "web_push_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_web_push_outbox_notification_subscription",
                columnNames = {"notification_id", "subscription_id"}),
        indexes = {
            @Index(
                    name = "ix_web_push_outbox_due",
                    columnList = "status, next_attempt_at, id"),
            @Index(
                    name = "ix_web_push_outbox_subscription_status",
                    columnList = "subscription_id, status")
        })
public class WebPushOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "recipient_member_id", nullable = false)
    private Long recipientMemberId;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WebPushOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "claim_token", length = 36)
    private String claimToken;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WebPushOutbox() {
    }

    public Long getId() {
        return id;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public Long getRecipientMemberId() {
        return recipientMemberId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public WebPushOutboxStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public String getClaimToken() {
        return claimToken;
    }

    public LocalDateTime getProcessingStartedAt() {
        return processingStartedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void claim(String token, LocalDateTime now) {
        if (status != WebPushOutboxStatus.PENDING
                && status != WebPushOutboxStatus.RETRY_WAIT
                && status != WebPushOutboxStatus.PROCESSING) {
            throw new IllegalStateException("선점할 수 없는 Web Push Outbox 상태입니다: " + status);
        }
        status = WebPushOutboxStatus.PROCESSING;
        attemptCount += 1;
        claimToken = token;
        processingStartedAt = now;
        updatedAt = now;
    }

    public void markSent(LocalDateTime now) {
        requireProcessing();
        status = WebPushOutboxStatus.SENT;
        sentAt = now;
        lastErrorCode = null;
        clearClaim(now);
    }

    public void scheduleRetry(LocalDateTime nextAttempt, String errorCode, LocalDateTime now) {
        requireProcessing();
        status = WebPushOutboxStatus.RETRY_WAIT;
        nextAttemptAt = nextAttempt;
        lastErrorCode = errorCode;
        clearClaim(now);
    }

    public void markFailed(String errorCode, LocalDateTime now) {
        requireProcessing();
        status = WebPushOutboxStatus.FAILED_PERMANENT;
        lastErrorCode = errorCode;
        clearClaim(now);
    }

    public void cancel(String reason, LocalDateTime now) {
        requireProcessing();
        status = WebPushOutboxStatus.CANCELLED;
        lastErrorCode = reason;
        clearClaim(now);
    }

    private void requireProcessing() {
        if (status != WebPushOutboxStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 Web Push Outbox만 결과를 기록할 수 있습니다.");
        }
    }

    private void clearClaim(LocalDateTime now) {
        claimToken = null;
        processingStartedAt = null;
        updatedAt = now;
    }
}
