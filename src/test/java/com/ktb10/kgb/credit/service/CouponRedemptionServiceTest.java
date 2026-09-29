package com.ktb10.kgb.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.credit.entity.CreditTransaction;
import com.ktb10.kgb.credit.entity.CreditTransactionType;
import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CouponRedemptionServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private CreditWalletRepository creditWalletRepository;
    @Mock
    private CreditTransactionRepository creditTransactionRepository;

    private CouponRedemptionService service;
    private CreditWallet wallet;

    @BeforeEach
    void setUp() {
        service = new CouponRedemptionService(
                creditWalletRepository,
                creditTransactionRepository,
                CLOCK,
                "Secret-2026",
                2);
        wallet = CreditWallet.open(
                Member.register(
                        OauthProvider.KAKAO,
                        "provider-id",
                        "traveler",
                        null,
                        null,
                        LocalDateTime.now(CLOCK)),
                LocalDateTime.now(CLOCK));
    }

    @Test
    void grantsConfiguredCreditsAndRecordsCouponLedgerEntry() {
        given(creditWalletRepository.findByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(wallet));

        var response = service.redeem(MEMBER_ID, " secret-2026 ");

        assertThat(response.grantedCredits()).isEqualTo(2);
        assertThat(response.creditBalance()).isEqualTo(2);
        ArgumentCaptor<CreditTransaction> captor =
                ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(CreditTransactionType.COUPON_GRANT);
        assertThat(captor.getValue().getIdempotencyKey())
                .startsWith("admin-coupon:" + MEMBER_ID + ":")
                .doesNotContain("SECRET-2026");
    }

    @Test
    void rejectsInvalidCouponWithoutLockingWallet() {
        assertThatThrownBy(() -> service.redeem(MEMBER_ID, "wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode().code())
                .isEqualTo("COUPON_INVALID");

        verify(creditWalletRepository, never()).findByMemberIdForUpdate(any());
    }

    @Test
    void allowsAdministratorCouponToBeUsedRepeatedly() {
        given(creditWalletRepository.findByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(wallet));

        var first = service.redeem(MEMBER_ID, "SECRET-2026");
        var second = service.redeem(MEMBER_ID, "SECRET-2026");

        assertThat(first.creditBalance()).isEqualTo(2);
        assertThat(second.creditBalance()).isEqualTo(4);
        verify(creditTransactionRepository, org.mockito.Mockito.times(2)).save(any());
    }
}
