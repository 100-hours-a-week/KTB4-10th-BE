package com.ktb10.kgb.credit.entity;

import com.ktb10.kgb.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;

/** 회원이 현재 보유한 생성권 총잔액을 저장합니다. */
@Entity
@Table(
        name = "credit_wallets",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_credit_wallets_member",
                columnNames = "member_id"))
public class CreditWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_credit_wallets_member"))
    private Member member;

    @Column(name = "credit_balance", nullable = false)
    private int creditBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CreditWallet() {
    }

    private CreditWallet(Member member, LocalDateTime now) {
        this.member = Objects.requireNonNull(member, "회원은 null일 수 없습니다.");
        this.createdAt = Objects.requireNonNull(now, "생성 시각은 null일 수 없습니다.");
        this.updatedAt = now;
    }

    public static CreditWallet open(Member member, LocalDateTime now) {
        return new CreditWallet(member, now);
    }

    public int grant(int amount, LocalDateTime now) {
        if (amount <= 0) {
            throw new IllegalArgumentException("지급할 생성권은 1개 이상이어야 합니다.");
        }
        creditBalance = Math.addExact(creditBalance, amount);
        updatedAt = Objects.requireNonNull(now, "수정 시각은 null일 수 없습니다.");
        return creditBalance;
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public int getCreditBalance() {
        return creditBalance;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
