package com.ktb10.kgb.credit.repository;

import com.ktb10.kgb.credit.entity.CreditWallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 회원별 생성권 지갑을 저장하고 조회합니다. */
public interface CreditWalletRepository extends JpaRepository<CreditWallet, Long> {

    Optional<CreditWallet> findByMemberId(Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from CreditWallet wallet where wallet.member.id = :memberId")
    Optional<CreditWallet> findByMemberIdForUpdate(@Param("memberId") Long memberId);
}
