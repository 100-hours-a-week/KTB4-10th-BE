package com.ktb10.kgb.credit.service;

import com.ktb10.kgb.credit.entity.CreditTransaction;
import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.member.entity.Member;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 신규 회원에게 지갑과 가입월 무료 생성권을 지급합니다. */
@Service
public class SignupCreditGrantService {

    static final int MONTHLY_FREE_CREDITS = 3;
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final CreditWalletRepository creditWalletRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final Clock clock;

    public SignupCreditGrantService(
            CreditWalletRepository creditWalletRepository,
            CreditTransactionRepository creditTransactionRepository,
            Clock clock) {
        this.creditWalletRepository = creditWalletRepository;
        this.creditTransactionRepository = creditTransactionRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void grantSignupMonth(Member member) {
        Objects.requireNonNull(member, "회원은 null일 수 없습니다.");
        if (member.getId() == null) {
            throw new IllegalArgumentException("가입월 생성권 지급 전 회원이 저장되어야 합니다.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String idempotencyKey = idempotencyKey(member.getId(), clock);
        if (creditTransactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }

        CreditWallet wallet = creditWalletRepository.findByMemberId(member.getId())
                .orElseGet(() -> creditWalletRepository.save(CreditWallet.open(member, now)));
        int balanceAfter = wallet.grant(MONTHLY_FREE_CREDITS, now);
        creditTransactionRepository.save(CreditTransaction.freeGrant(
                wallet,
                MONTHLY_FREE_CREDITS,
                balanceAfter,
                idempotencyKey,
                now));
    }

    static String idempotencyKey(Long memberId, Clock clock) {
        YearMonth month = YearMonth.from(clock.instant().atZone(SEOUL_ZONE));
        return "monthly-free:" + memberId + ":" + month.toString().replace("-", "");
    }
}
