package com.ktb10.kgb.credit.entity;

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

/** 생성권의 지급·사용·회수 이력을 변경 불가능한 원장으로 저장합니다. */
@Entity
@Table(
        name = "credit_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_credit_transactions_idempotency",
                columnNames = "idempotency_key"),
        indexes = @Index(
                name = "ix_credit_transactions_wallet_created",
                columnList = "wallet_id, created_at"))
public class CreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "wallet_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_credit_transactions_wallet"))
    private CreditWallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CreditTransactionType type;

    @Column(name = "credit_delta", nullable = false)
    private int creditDelta;

    @Column(name = "credit_balance_after", nullable = false)
    private int creditBalanceAfter;

    @Column(name = "generation_job_id")
    private Long generationJobId;

    @Column(name = "idempotency_key", nullable = false, length = 150)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CreditTransaction() {
    }

    private CreditTransaction(
            CreditWallet wallet,
            CreditTransactionType type,
            int creditDelta,
            int creditBalanceAfter,
            String idempotencyKey,
            LocalDateTime createdAt) {
        this.wallet = Objects.requireNonNull(wallet, "생성권 지갑은 null일 수 없습니다.");
        this.type = Objects.requireNonNull(type, "생성권 거래 유형은 null일 수 없습니다.");
        this.creditDelta = creditDelta;
        this.creditBalanceAfter = creditBalanceAfter;
        this.idempotencyKey = requireText(idempotencyKey, "멱등 키");
        this.createdAt = Objects.requireNonNull(createdAt, "생성 시각은 null일 수 없습니다.");
        if (creditBalanceAfter < 0) {
            throw new IllegalArgumentException("처리 후 생성권 잔액은 음수일 수 없습니다.");
        }
    }

    public static CreditTransaction freeGrant(
            CreditWallet wallet,
            int amount,
            int balanceAfter,
            String idempotencyKey,
            LocalDateTime createdAt) {
        if (amount <= 0) {
            throw new IllegalArgumentException("무료 지급 생성권은 1개 이상이어야 합니다.");
        }
        return new CreditTransaction(
                wallet,
                CreditTransactionType.FREE_GRANT,
                amount,
                balanceAfter,
                idempotencyKey,
                createdAt);
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

    public CreditWallet getWallet() {
        return wallet;
    }

    public CreditTransactionType getType() {
        return type;
    }

    public int getCreditDelta() {
        return creditDelta;
    }

    public int getCreditBalanceAfter() {
        return creditBalanceAfter;
    }

    public Long getGenerationJobId() {
        return generationJobId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
