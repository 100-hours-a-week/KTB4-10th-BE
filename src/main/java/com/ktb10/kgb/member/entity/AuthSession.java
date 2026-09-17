package com.ktb10.kgb.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

/** 원문 세션 ID 대신 SHA-256 해시와 만료·폐기 상태를 저장하는 서비스 세션입니다. */
@Entity
@Table(
        name = "auth_sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_auth_sessions_session_id",
                columnNames = "session_id_hash"),
        indexes = @Index(
                name = "ix_auth_sessions_member_state",
                columnList = "member_id, revoked_at, expires_at"))
public class AuthSession {

    private static final int SHA_256_BYTES = 32;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_auth_sessions_member"))
    private Member member;

    @Column(name = "session_id_hash", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] sessionIdHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AuthSession() {
    }

    private AuthSession(
            Member member,
            byte[] sessionIdHash,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        this.member = Objects.requireNonNull(member, "회원은 null일 수 없습니다.");
        this.sessionIdHash = copyHash(sessionIdHash);
        this.expiresAt = Objects.requireNonNull(expiresAt, "만료 시각은 null일 수 없습니다.");
        this.createdAt = Objects.requireNonNull(createdAt, "생성 시각은 null일 수 없습니다.");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("만료 시각은 생성 시각 이후여야 합니다.");
        }
        this.lastUsedAt = createdAt;
    }

    public static AuthSession issue(
            Member member,
            byte[] sessionIdHash,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        return new AuthSession(member, sessionIdHash, expiresAt, createdAt);
    }

    public boolean isUsable(LocalDateTime now, Duration idleTimeout) {
        Objects.requireNonNull(now, "현재 시각은 null일 수 없습니다.");
        Objects.requireNonNull(idleTimeout, "유휴 만료 시간은 null일 수 없습니다.");
        LocalDateTime idleBaseTime = lastUsedAt == null ? createdAt : lastUsedAt;
        LocalDateTime idleExpiresAt = idleBaseTime.plus(idleTimeout);
        return revokedAt == null && now.isBefore(expiresAt) && now.isBefore(idleExpiresAt);
    }

    public void recordUse(LocalDateTime usedAt) {
        LocalDateTime requestedTime = Objects.requireNonNull(usedAt, "사용 시각은 null일 수 없습니다.");
        if (lastUsedAt != null && requestedTime.isBefore(lastUsedAt)) {
            throw new IllegalArgumentException("사용 시각은 마지막 사용 시각보다 이전일 수 없습니다.");
        }
        lastUsedAt = requestedTime;
    }

    public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            revokedAt = Objects.requireNonNull(now, "현재 시각은 null일 수 없습니다.");
        }
    }

    private static byte[] copyHash(byte[] sessionIdHash) {
        Objects.requireNonNull(sessionIdHash, "세션 ID 해시는 null일 수 없습니다.");
        if (sessionIdHash.length != SHA_256_BYTES) {
            throw new IllegalArgumentException("세션 ID 해시는 32바이트여야 합니다.");
        }
        return Arrays.copyOf(sessionIdHash, sessionIdHash.length);
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public byte[] getSessionIdHash() {
        return Arrays.copyOf(sessionIdHash, sessionIdHash.length);
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
