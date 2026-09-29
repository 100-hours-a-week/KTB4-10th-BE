package com.ktb10.kgb.credit.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.credit.dto.response.CouponRedeemResponse;
import com.ktb10.kgb.credit.entity.CreditTransaction;
import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.error.CreditErrorCode;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 환경변수의 쿠폰을 검증하고 회원별로 1회 지겹합니다. */
@Service
public class CouponRedemptionService {

    private final CreditWalletRepository creditWalletRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final Clock clock;
    private final String configuredCode;
    private final int creditAmount;

    public CouponRedemptionService(
            CreditWalletRepository creditWalletRepository,
            CreditTransactionRepository creditTransactionRepository,
            Clock clock,
            @Value("${COUPON_CODE:}") String configuredCode,
            @Value("${COUPON_CREDIT_AMOUNT:1}") int creditAmount) {
        this.creditWalletRepository = creditWalletRepository;
        this.creditTransactionRepository = creditTransactionRepository;
        this.clock = clock;
        this.configuredCode = normalize(configuredCode);
        this.creditAmount = creditAmount;
    }

    @Transactional
    public CouponRedeemResponse redeem(Long memberId, String submittedCode) {
        if (configuredCode.isEmpty() || creditAmount <= 0) {
            throw new BusinessException(CreditErrorCode.COUPON_UNAVAILABLE);
        }

        String normalizedCode = normalize(submittedCode);
        if (!MessageDigest.isEqual(
                configuredCode.getBytes(StandardCharsets.UTF_8),
                normalizedCode.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(CreditErrorCode.COUPON_INVALID);
        }

        CreditWallet wallet = creditWalletRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.COUPON_UNAVAILABLE));
        LocalDateTime now = LocalDateTime.now(clock);
        int balanceAfter = wallet.grant(creditAmount, now);
        creditTransactionRepository.save(CreditTransaction.couponGrant(
                wallet,
                creditAmount,
                balanceAfter,
                "admin-coupon:" + memberId + ":" + UUID.randomUUID(),
                now));
        return new CouponRedeemResponse(creditAmount, balanceAfter);
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
