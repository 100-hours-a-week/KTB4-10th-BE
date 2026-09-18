package com.ktb10.kgb.credit.entity;

/** 생성권 잔액이 변경된 원인을 나타냅니다. */
public enum CreditTransactionType {
    FREE_GRANT,
    PURCHASE_GRANT,
    CONSUME,
    REVOKE,
    ADJUSTMENT
}
