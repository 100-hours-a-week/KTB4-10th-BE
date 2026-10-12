package com.ktb10.kgb.credit.service;

import com.ktb10.kgb.credit.entity.CreditTransaction;
import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 회원별 월 무료 생성권을 최소 3개까지 한 번만 보충합니다. */
@Service
public class MonthlyFreeCreditRefillService {

    static final int MONTHLY_MINIMUM_BALANCE = 3;

    private final MemberRepository memberRepository;
    private final CreditWalletRepository creditWalletRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final Clock clock;

    public MonthlyFreeCreditRefillService(
            MemberRepository memberRepository,
            CreditWalletRepository creditWalletRepository,
            CreditTransactionRepository creditTransactionRepository,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.creditWalletRepository = creditWalletRepository;
        this.creditTransactionRepository = creditTransactionRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonthlyFreeCreditRefillResult refill(Long memberId, YearMonth targetMonth) {
        Objects.requireNonNull(memberId, "회원 ID는 null일 수 없습니다.");
        Objects.requireNonNull(targetMonth, "처리 대상 월은 null일 수 없습니다.");

        if (memberRepository.findActiveByIdForUpdate(memberId).isEmpty()) {
            return MonthlyFreeCreditRefillResult.MEMBER_NOT_ELIGIBLE;
        }

        CreditWallet wallet = creditWalletRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalStateException(
                        "월 무료 생성권을 보충할 회원 지갑을 찾을 수 없습니다. memberId=" + memberId));
        String idempotencyKey = SignupCreditGrantService.idempotencyKey(memberId, targetMonth);
        if (creditTransactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return MonthlyFreeCreditRefillResult.ALREADY_PROCESSED;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int refillAmount = wallet.refillToMinimum(MONTHLY_MINIMUM_BALANCE, now);
        creditTransactionRepository.save(CreditTransaction.monthlyFreeRefresh(
                wallet,
                refillAmount,
                wallet.getCreditBalance(),
                idempotencyKey,
                now));
        return refillAmount > 0
                ? MonthlyFreeCreditRefillResult.REFILLED
                : MonthlyFreeCreditRefillResult.NO_BALANCE_CHANGE;
    }
}
