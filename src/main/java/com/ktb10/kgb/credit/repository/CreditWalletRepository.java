package com.ktb10.kgb.credit.repository;

import com.ktb10.kgb.credit.entity.CreditWallet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 회원별 생성권 지갑을 저장하고 조회합니다. */
public interface CreditWalletRepository extends JpaRepository<CreditWallet, Long> {

    Optional<CreditWallet> findByMemberId(Long memberId);
}
