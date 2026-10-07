package com.ktb10.kgb.member.entity;

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
import java.util.Arrays;
import java.util.Objects;

/** 회원의 브라우저별 표준 Web Push 구독과 암호화 키를 보관합니다. */
@Entity
@Table(
        name = "web_push_subscriptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_web_push_subscriptions_endpoint_hash",
                columnNames = "endpoint_hash"),
        indexes = {
            @Index(
                    name = "ix_web_push_subscriptions_member_status",
                    columnList = "member_id, status"),
            @Index(
                    name = "ix_web_push_subscriptions_session_status",
                    columnList = "auth_session_id, status")
        })
public class WebPushSubscription {

    private static final int SHA_256_BYTES = 32;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_web_push_subscriptions_member"))
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "auth_session_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_web_push_subscriptions_auth_session"))
    private AuthSession authSession;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String endpoint;

    @Column(name = "endpoint_hash", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] endpointHash;

    @Column(nullable = false, length = 255)
    private String p256dh;

    @Column(name = "auth_secret", nullable = false, length = 255)
    private String authSecret;

    @Column(name = "expiration_at")
    private LocalDateTime expirationAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebPushSubscriptionStatus status;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "last_failure_at")
    private LocalDateTime lastFailureAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WebPushSubscription() {
    }

    private WebPushSubscription(
            Member member,
            AuthSession authSession,
            String endpoint,
            byte[] endpointHash,
            String p256dh,
            String authSecret,
            LocalDateTime expirationAt,
            LocalDateTime now) {
        this.member = Objects.requireNonNull(member, "회원은 null일 수 없습니다.");
        this.authSession = Objects.requireNonNull(authSession, "인증 세션은 null일 수 없습니다.");
        this.endpoint = requireText(endpoint, "Push endpoint");
        this.endpointHash = copyHash(endpointHash);
        this.p256dh = requireText(p256dh, "p256dh 키");
        this.authSecret = requireText(authSecret, "auth 키");
        this.expirationAt = expirationAt;
        this.status = WebPushSubscriptionStatus.ACTIVE;
        this.failureCount = 0;
        this.createdAt = Objects.requireNonNull(now, "생성 시각은 null일 수 없습니다.");
        this.updatedAt = now;
    }

    public static WebPushSubscription register(
            Member member,
            AuthSession authSession,
            String endpoint,
            byte[] endpointHash,
            String p256dh,
            String authSecret,
            LocalDateTime expirationAt,
            LocalDateTime now) {
        return new WebPushSubscription(
                member,
                authSession,
                endpoint,
                endpointHash,
                p256dh,
                authSecret,
                expirationAt,
                now);
    }

    public void renew(
            Member member,
            AuthSession authSession,
            String endpoint,
            String p256dh,
            String authSecret,
            LocalDateTime expirationAt,
            LocalDateTime now) {
        this.member = Objects.requireNonNull(member, "회원은 null일 수 없습니다.");
        this.authSession = Objects.requireNonNull(authSession, "인증 세션은 null일 수 없습니다.");
        this.endpoint = requireText(endpoint, "Push endpoint");
        this.p256dh = requireText(p256dh, "p256dh 키");
        this.authSecret = requireText(authSecret, "auth 키");
        this.expirationAt = expirationAt;
        this.status = WebPushSubscriptionStatus.ACTIVE;
        this.failureCount = 0;
        this.lastFailureAt = null;
        this.revokedAt = null;
        this.updatedAt = Objects.requireNonNull(now, "갱신 시각은 null일 수 없습니다.");
    }

    public void revoke(LocalDateTime now) {
        if (status != WebPushSubscriptionStatus.REVOKED) {
            status = WebPushSubscriptionStatus.REVOKED;
            revokedAt = Objects.requireNonNull(now, "폐기 시각은 null일 수 없습니다.");
            updatedAt = now;
        }
    }

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }

    public boolean isActive() {
        return status == WebPushSubscriptionStatus.ACTIVE;
    }

    private static byte[] copyHash(byte[] endpointHash) {
        Objects.requireNonNull(endpointHash, "endpoint 해시는 null일 수 없습니다.");
        if (endpointHash.length != SHA_256_BYTES) {
            throw new IllegalArgumentException("endpoint 해시는 32바이트여야 합니다.");
        }
        return Arrays.copyOf(endpointHash, endpointHash.length);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 값은 비어 있을 수 없습니다.");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public AuthSession getAuthSession() {
        return authSession;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public byte[] getEndpointHash() {
        return Arrays.copyOf(endpointHash, endpointHash.length);
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuthSecret() {
        return authSecret;
    }

    public LocalDateTime getExpirationAt() {
        return expirationAt;
    }

    public WebPushSubscriptionStatus getStatus() {
        return status;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public LocalDateTime getLastSuccessAt() {
        return lastSuccessAt;
    }

    public LocalDateTime getLastFailureAt() {
        return lastFailureAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
