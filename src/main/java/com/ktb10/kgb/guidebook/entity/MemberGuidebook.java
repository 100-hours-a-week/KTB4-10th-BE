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
        name = "member_guidebooks",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_member_guidebooks_member_guidebook",
                columnNames = {"member_id", "guidebook_id"}),
        indexes = @Index(
                name = "ix_member_guidebooks_member_created",
                columnList = "member_id, deleted_at, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberGuidebook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_member_guidebooks_member"))
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "guidebook_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_member_guidebooks_guidebook"))
    private Guidebook guidebook;

    @Enumerated(EnumType.STRING)
    @Column(name = "acquisition_type", length = 20, nullable = false)
    private AcquisitionType acquisitionType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static MemberGuidebook create(
            Member member,
            Guidebook guidebook,
            AcquisitionType acquisitionType,
            LocalDateTime createdAt) {
        MemberGuidebook memberGuidebook = new MemberGuidebook();
        memberGuidebook.member = Objects.requireNonNull(member);
        memberGuidebook.guidebook = Objects.requireNonNull(guidebook);
        memberGuidebook.acquisitionType = Objects.requireNonNull(acquisitionType);
        memberGuidebook.createdAt = Objects.requireNonNull(createdAt);
        return memberGuidebook;
    }

    public void softDelete(LocalDateTime deletedAt) {
        this.deletedAt = Objects.requireNonNull(deletedAt);
    }

}
