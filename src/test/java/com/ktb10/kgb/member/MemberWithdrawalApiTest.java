package com.ktb10.kgb.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb10.kgb.common.security.SessionCookieResolver;
import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.credit.entity.CreditTransaction;
import com.ktb10.kgb.credit.entity.CreditTransactionType;
import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-withdrawal-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "SESSION_COOKIE_SECURE=false"
})
@AutoConfigureMockMvc
@Import(MemberWithdrawalApiTest.FixedClockConfiguration.class)
class MemberWithdrawalApiTest {

    private static final Instant CURRENT_INSTANT = Instant.parse("2026-09-27T06:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(CURRENT_INSTANT, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionIdHasher sessionIdHasher;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private CreditWalletRepository creditWalletRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @Autowired
    private GenerationJobRepository generationJobRepository;

    @BeforeEach
    void cleanUp() {
        creditTransactionRepository.deleteAll();
        generationJobRepository.deleteAll();
        creditWalletRepository.deleteAll();
        authSessionRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void withdrawalRevokesMemberSessionsCreditsAndActiveGenerationJob() throws Exception {
        Member member = saveMember("withdrawal-member");
        AuthSession currentSession = issueSession(member, "current-session");
        AuthSession otherSession = issueSession(member, "other-session");
        CreditWallet wallet = CreditWallet.open(member, NOW.minusDays(1));
        int balance = wallet.grant(3, NOW.minusDays(1));
        creditWalletRepository.saveAndFlush(wallet);
        creditTransactionRepository.saveAndFlush(CreditTransaction.freeGrant(
                wallet,
                3,
                balance,
                "monthly-free:" + member.getId() + ":202609",
                NOW.minusDays(1)));
        GenerationJob job = generationJobRepository.saveAndFlush(GenerationJob.createInitial(
                member,
                "{\"region\":\"서울\"}",
                "withdrawal-generation",
                NOW.minusMinutes(10)));
        Csrf csrf = csrf();

        MvcResult result = mockMvc.perform(delete("/api/v1/members/me")
                        .cookie(sessionCookie("current-session"), csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.cookie().getValue()))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(value -> value.startsWith("XSRF-TOKEN="))
                .anyMatch(value -> value.startsWith("KGB_SESSION=;"));
        Member withdrawn = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(withdrawn.getDeletedAt()).isEqualTo(NOW);
        assertThat(withdrawn.getOauthSubject()).isEqualTo("withdrawn:" + member.getId());
        assertThat(authSessionRepository.findById(currentSession.getId()).orElseThrow()
                .getRevokedAt()).isEqualTo(NOW);
        assertThat(authSessionRepository.findById(otherSession.getId()).orElseThrow()
                .getRevokedAt()).isEqualTo(NOW);
        assertThat(creditWalletRepository.findById(wallet.getId()).orElseThrow()
                .getCreditBalance()).isZero();
        assertThat(creditTransactionRepository.findAll())
                .filteredOn(transaction -> transaction.getType() == CreditTransactionType.REVOKE)
                .singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getCreditDelta()).isEqualTo(-3);
                    assertThat(transaction.getCreditBalanceAfter()).isZero();
                    assertThat(transaction.getIdempotencyKey())
                            .isEqualTo("withdrawal-revoke:" + member.getId());
                });
        GenerationJob canceledJob = generationJobRepository.findById(job.getId()).orElseThrow();
        assertThat(canceledJob.getStatus()).isEqualTo(GenerationStatus.CANCELED);
        assertThat(canceledJob.getCancelRequestedAt()).isEqualTo(NOW);
        assertThat(canceledJob.getCompletedAt()).isEqualTo(NOW);

        mockMvc.perform(get("/api/v1/members/me")
                        .cookie(sessionCookie("current-session")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"));
    }

    @Test
    void withdrawalWithZeroBalanceDoesNotCreateRevokeTransaction() throws Exception {
        Member member = saveMember("zero-balance-member");
        issueSession(member, "zero-balance-session");
        creditWalletRepository.saveAndFlush(CreditWallet.open(member, NOW.minusDays(1)));
        Csrf csrf = csrf();

        mockMvc.perform(delete("/api/v1/members/me")
                        .cookie(sessionCookie("zero-balance-session"), csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.cookie().getValue()))
                .andExpect(status().isNoContent());

        assertThat(creditTransactionRepository.findAll()).isEmpty();
    }

    @Test
    void withdrawalRequiresAuthenticationAndCsrfToken() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_FORBIDDEN"));

        Member member = saveMember("csrf-withdrawal-member");
        issueSession(member, "csrf-withdrawal-session");
        mockMvc.perform(delete("/api/v1/members/me")
                        .cookie(sessionCookie("csrf-withdrawal-session")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_FORBIDDEN"));
    }

    private Member saveMember(String oauthSubject) {
        return memberRepository.saveAndFlush(Member.register(
                OauthProvider.KAKAO,
                oauthSubject,
                "여행자",
                null,
                null,
                NOW.minusDays(1)));
    }

    private AuthSession issueSession(Member member, String rawSessionId) {
        return authSessionRepository.saveAndFlush(AuthSession.issue(
                member,
                sessionIdHasher.hash(rawSessionId),
                NOW.plusHours(8),
                NOW.minusMinutes(1)));
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        return new Csrf(result.getResponse().getCookie("XSRF-TOKEN"));
    }

    private static Cookie sessionCookie(String value) {
        return new Cookie(SessionCookieResolver.COOKIE_NAME, value);
    }

    private record Csrf(Cookie cookie) {
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(CURRENT_INSTANT, ZoneOffset.UTC);
        }
    }
}
