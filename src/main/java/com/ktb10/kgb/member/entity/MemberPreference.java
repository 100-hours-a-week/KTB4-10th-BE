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
import java.util.Objects;

/** 회원이 선택한 하나의 취향 코드를 저장합니다. */
@Entity
@Table(
        name = "member_preferences",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_member_preferences_selection",
                columnNames = {"member_id", "preference_type", "preference_code"}),
        indexes = @Index(
                name = "ix_member_preferences_code",
                columnList = "preference_type, preference_code"))
public class MemberPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_member_preferences_member"))
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "preference_type", nullable = false, length = 20)
    private PreferenceType preferenceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "preference_code", nullable = false, length = 50)
    private PreferenceCode preferenceCode;

    protected MemberPreference() {
    }

    private MemberPreference(Member member, PreferenceCode preferenceCode) {
        this.member = Objects.requireNonNull(member, "회원은 null일 수 없습니다.");
        this.preferenceCode = Objects.requireNonNull(preferenceCode, "취향 코드는 null일 수 없습니다.");
        this.preferenceType = preferenceCode.type();
    }

    public static MemberPreference select(Member member, PreferenceCode preferenceCode) {
        return new MemberPreference(member, preferenceCode);
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public PreferenceType getPreferenceType() {
        return preferenceType;
    }

    public PreferenceCode getPreferenceCode() {
        return preferenceCode;
    }
}
