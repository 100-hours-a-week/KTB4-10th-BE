package com.ktb10.kgb.credit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.credit.entity.CreditTransactionType;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({SignupCreditGrantService.class, SignupCreditGrantServiceTest.FixedClockConfiguration.class})
class SignupCreditGrantServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-30T15:30:00Z");

    @Autowired
    private SignupCreditGrantService signupCreditGrantService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CreditWalletRepository creditWalletRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @Test
    void grantsThreeCreditsOnceForSeoulSignupMonth() {
        Member member = memberRepository.save(Member.register(
                OauthProvider.KAKAO,
                "kakao-signup-credit",
                "여행자",
                null,
                null,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)));

        signupCreditGrantService.grantSignupMonth(member);
        signupCreditGrantService.grantSignupMonth(member);

        var wallet = creditWalletRepository.findByMemberId(member.getId()).orElseThrow();
        var transactions = creditTransactionRepository.findAll();
        assertThat(wallet.getCreditBalance()).isEqualTo(3);
        assertThat(transactions).singleElement().satisfies(transaction -> {
            assertThat(transaction.getType()).isEqualTo(CreditTransactionType.FREE_GRANT);
            assertThat(transaction.getCreditDelta()).isEqualTo(3);
            assertThat(transaction.getCreditBalanceAfter()).isEqualTo(3);
            assertThat(transaction.getIdempotencyKey())
                    .isEqualTo("monthly-free:" + member.getId() + ":202610");
        });
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
