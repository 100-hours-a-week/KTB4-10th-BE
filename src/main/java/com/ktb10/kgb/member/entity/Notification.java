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
import java.time.LocalDateTime;
import java.util.Objects;

/** 회원에게 노출할 미읽음 인앱 알림입니다. 읽으면 행을 물리 삭제합니다. */
@Entity
@Table(
        name = "notifications",
        indexes = @Index(
                name = "ix_notifications_recipient_created",
                columnList = "recipient_member_id, created_at"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "recipient_member_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_notifications_recipient"))
    private Member recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 30)
    private NotificationReferenceType referenceType;

    @Column(name = "reference_id", length = 50)
    private String referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    private Notification(
            Member recipient,
            NotificationType type,
            String title,
            String body,
            NotificationReferenceType referenceType,
            String referenceId,
            LocalDateTime createdAt) {
        this.recipient = Objects.requireNonNull(recipient, "알림 수신 회원은 null일 수 없습니다.");
        this.type = Objects.requireNonNull(type, "알림 유형은 null일 수 없습니다.");
        this.title = requireText(title, "알림 제목");
        this.body = requireText(body, "알림 본문");
        validateReferencePair(referenceType, referenceId);
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.createdAt = Objects.requireNonNull(createdAt, "생성 시각은 null일 수 없습니다.");
    }

    public static Notification create(
            Member recipient,
            NotificationType type,
            String title,
            String body,
            NotificationReferenceType referenceType,
            String referenceId,
            LocalDateTime createdAt) {
        return new Notification(
                recipient,
                type,
                title,
                body,
                referenceType,
                referenceId,
                createdAt);
    }

    private static void validateReferencePair(
            NotificationReferenceType referenceType,
            String referenceId) {
        boolean hasType = referenceType != null;
        boolean hasId = referenceId != null && !referenceId.isBlank();
        if (hasType != hasId) {
            throw new IllegalArgumentException("알림 참조 유형과 참조 ID는 둘 다 존재하거나 둘 다 없어야 합니다.");
        }
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

    public Member getRecipient() {
        return recipient;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public NotificationReferenceType getReferenceType() {
        return referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
