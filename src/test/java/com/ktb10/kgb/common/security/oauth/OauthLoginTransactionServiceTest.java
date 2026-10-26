package com.ktb10.kgb.common.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.credit.service.SignupCreditGrantService;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.session.ServiceSessionLifecycleStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({OauthLoginTransactionService.class, SignupCreditGrantService.class, SessionIdHasher.class,
        OauthLoginTransactionServiceTest.FixedClockConfiguration.class})
class OauthLoginTransactionServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-18T05:00:00Z");
    @Autowired private OauthLoginTransactionService service;
    @Autowired private MemberRepository members;
    @Autowired private CreditWalletRepository wallets;
    @Autowired private CreditTransactionRepository transactions;
    @MockitoBean private ServiceSessionLifecycleStore sessions;

    @Test
    void newMemberAndCreditsAreStoredInMysqlWhileSessionIsIssuedInRedis() {
        var result = service.complete(new KakaoOauthUser("new", "여행자", null, null), "token");
        var member = members.findByOauthProviderAndOauthSubjectAndDeletedAtIsNull(
                OauthProvider.KAKAO, "new").orElseThrow();
        assertThat(result.memberStatus()).isEqualTo(member.getStatus());
        assertThat(wallets.findByMemberId(member.getId()).orElseThrow().getCreditBalance()).isEqualTo(3);
        assertThat(transactions.count()).isOne();
        verify(sessions).issueReplacingForMember(new SessionIdHasher().hash("token"),
                member.getId(), Duration.ofHours(8), Duration.ofMinutes(30));
    }

    @Test
    void existingMemberProfileIsPreservedAndSignupCreditsAreNotDuplicated() {
        var member = members.saveAndFlush(Member.register(OauthProvider.KAKAO, "old", "기존",
                "old@example.com", null, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)));
        service.complete(new KakaoOauthUser("old", "변경", "new@example.com", null), "token");
        assertThat(members.findById(member.getId()).orElseThrow().getNickname()).isEqualTo("기존");
        assertThat(member.getEmail()).isEqualTo("old@example.com");
        assertThat(wallets.count()).isZero();
        verify(sessions).issueReplacingForMember(any(byte[].class), eq(member.getId()),
                eq(Duration.ofHours(8)), eq(Duration.ofMinutes(30)));
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
