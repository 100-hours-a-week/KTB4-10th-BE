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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** 반복 실행 가능한 알림 생산자의 영속 중복 방지 기록입니다. */
@Entity
@Table(
        name = "notification_occurrences",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_notification_occurrences_dedup",
                columnNames = {
                    "recipient_member_id",
                    "type",
                    "reference_type",
                    "reference_id",
                    "occurrence_key"
                }))
public class NotificationOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "recipient_member_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_notification_occurrences_recipient"))
    private Member recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30)
    private NotificationReferenceType referenceType;

    @Column(name = "reference_id", nullable = false, length = 50)
    private String referenceId;

    @Column(name = "occurrence_key", nullable = false, length = 50)
    private String occurrenceKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected NotificationOccurrence() {
    }
}
