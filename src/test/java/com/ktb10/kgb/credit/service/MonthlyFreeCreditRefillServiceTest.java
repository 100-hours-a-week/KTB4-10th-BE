package com.ktb10.kgb.credit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        MonthlyFreeCreditRefillService.class,
        MonthlyFreeCreditRefillServiceTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MonthlyFreeCreditRefillServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:05:00Z");
    private static final YearMonth TARGET_MONTH = YearMonth.of(2026, 10);

    @Autowired
    private MonthlyFreeCreditRefillService refillService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CreditWalletRepository creditWalletRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @AfterEach
    void cleanUp() {
        creditTransactionRepository.deleteAll();
        creditWalletRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @ParameterizedTest
    @MethodSource("balances")
    void refillsOnlyUpToThree(int initialBalance, int expectedDelta) {
        Member member = saveMemberWithWallet(initialBalance);

        MonthlyFreeCreditRefillResult result = refillService.refill(
                member.getId(),
                TARGET_MONTH);

        assertThat(result).isEqualTo(expectedDelta > 0
                ? MonthlyFreeCreditRefillResult.REFILLED
                : MonthlyFreeCreditRefillResult.NO_BALANCE_CHANGE);
        CreditWallet wallet = creditWalletRepository.findByMemberId(member.getId()).orElseThrow();
        assertThat(wallet.getCreditBalance()).isEqualTo(Math.max(3, initialBalance));
        assertThat(creditTransactionRepository.findAll()).singleElement().satisfies(transaction -> {
            assertThat(transaction.getCreditDelta()).isEqualTo(expectedDelta);
            assertThat(transaction.getCreditBalanceAfter()).isEqualTo(Math.max(3, initialBalance));
            assertThat(transaction.getIdempotencyKey())
                    .isEqualTo("monthly-free:" + member.getId() + ":202610");
        });
    }

    @Test
    void doesNotRefillAgainAfterCreditsAreConsumedInSameMonth() {
        Member member = saveMemberWithWallet(1);
        assertThat(refillService.refill(member.getId(), TARGET_MONTH))
                .isEqualTo(MonthlyFreeCreditRefillResult.REFILLED);

        CreditWallet wallet = creditWalletRepository.findByMemberId(member.getId()).orElseThrow();
        wallet.consume(1, LocalDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)));
        creditWalletRepository.save(wallet);

        assertThat(refillService.refill(member.getId(), TARGET_MONTH))
                .isEqualTo(MonthlyFreeCreditRefillResult.ALREADY_PROCESSED);
        assertThat(creditWalletRepository.findByMemberId(member.getId()).orElseThrow()
                .getCreditBalance()).isEqualTo(2);
        assertThat(creditTransactionRepository.count()).isEqualTo(1);
    }

    @Test
    void skipsWithdrawnMember() {
        Member member = saveMemberWithWallet(0);
        member.withdraw("withdrawn-test-subject", LocalDateTime.now());
        memberRepository.save(member);

        assertThat(refillService.refill(member.getId(), TARGET_MONTH))
                .isEqualTo(MonthlyFreeCreditRefillResult.MEMBER_NOT_ELIGIBLE);
        assertThat(creditTransactionRepository.count()).isZero();
    }

    private Member saveMemberWithWallet(int balance) {
        LocalDateTime now = LocalDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC));
        Member member = memberRepository.save(Member.register(
                OauthProvider.KAKAO,
                "monthly-refill-" + System.nanoTime(),
                "여행자",
                null,
                null,
                now));
        CreditWallet wallet = CreditWallet.open(member, now);
        if (balance > 0) {
            wallet.grant(balance, now);
        }
        creditWalletRepository.save(wallet);
        return member;
    }

    private static Stream<Arguments> balances() {
        return Stream.of(
                Arguments.of(0, 3),
                Arguments.of(1, 2),
                Arguments.of(2, 1),
                Arguments.of(3, 0),
                Arguments.of(7, 0));
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
