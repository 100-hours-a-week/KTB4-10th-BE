package com.ktb10.kgb.credit.repository;

import com.ktb10.kgb.credit.entity.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

/** 생성권 증감 원장을 저장하고 멱등 키 중복을 확인합니다. */
public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);
}
