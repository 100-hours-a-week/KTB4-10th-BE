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
import java.util.Objects;

/** OAuth 식별 정보, 프로필과 서비스 설정을 보관하는 회원 Entity입니다. */
@Entity
@Table(
        name = "members",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_members_oauth",
                columnNames = {"oauth_provider", "oauth_subject"}),
        indexes = @Index(name = "ix_members_status", columnList = "status"))
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", nullable = false, length = 20)
    private OauthProvider oauthProvider;

    @Column(name = "oauth_subject", nullable = false, length = 255)
    private String oauthSubject;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 254)
    private String email;

    @Column(name = "profile_image_url", length = 2048)
    private String profileImageUrl;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Member() {
    }

    private Member(
            OauthProvider oauthProvider,
            String oauthSubject,
            String nickname,
            String email,
            String profileImageUrl,
            LocalDateTime now) {
        this.oauthProvider = Objects.requireNonNull(oauthProvider, "oauthProvider must not be null");
        this.oauthSubject = requireText(oauthSubject, "oauthSubject");
        this.nickname = requireText(nickname, "nickname");
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.languageCode = "ko";
        this.status = MemberStatus.ONBOARDING;
        this.pushEnabled = true;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public static Member register(
            OauthProvider oauthProvider,
            String oauthSubject,
            String nickname,
            String email,
            String profileImageUrl,
            LocalDateTime now) {
        return new Member(oauthProvider, oauthSubject, nickname, email, profileImageUrl, now);
    }

    public void activate(LocalDateTime now) {
        ensureNotDeleted();
        status = MemberStatus.ACTIVE;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void changePushEnabled(boolean enabled, LocalDateTime now) {
        ensureNotDeleted();
        pushEnabled = enabled;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void withdraw(String deidentifiedOauthSubject, LocalDateTime now) {
        ensureNotDeleted();
        oauthSubject = requireText(deidentifiedOauthSubject, "deidentifiedOauthSubject");
        deletedAt = Objects.requireNonNull(now, "now must not be null");
        updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private void ensureNotDeleted() {
        if (isDeleted()) {
            throw new IllegalStateException("탈퇴한 회원은 변경할 수 없습니다.");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public OauthProvider getOauthProvider() {
        return oauthProvider;
    }

    public String getOauthSubject() {
        return oauthSubject;
    }

    public String getNickname() {
        return nickname;
    }

    public String getEmail() {
        return email;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
