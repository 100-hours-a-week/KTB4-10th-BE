package com.ktb10.kgb.common.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.credit.service.SignupCreditGrantService;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
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
@Import({
        OauthLoginTransactionService.class,
        SignupCreditGrantService.class,
        SessionIdHasher.class,
        OauthLoginTransactionServiceTest.FixedClockConfiguration.class
})
class OauthLoginTransactionServiceTest {

    private static final Instant CURRENT_INSTANT = Instant.parse("2026-09-18T05:00:00Z");
    private static final LocalDateTime NOW =
            LocalDateTime.ofInstant(CURRENT_INSTANT, ZoneOffset.UTC);

    @Autowired
    private OauthLoginTransactionService transactionService;

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
    private EntityManager entityManager;

    @BeforeEach
    void cleanUp() {
        creditTransactionRepository.deleteAll();
        creditWalletRepository.deleteAll();
        authSessionRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void newKakaoUserCreatesMemberCreditsAndServiceSession() {
        OauthLoginResult result = transactionService.complete(
                new KakaoOauthUser(
                        "new-kakao-user",
                        "새 여행자",
                        null,
                        "https://example.com/profile.png"),
                "new-service-session");

        Member member = memberRepository
                .findByOauthProviderAndOauthSubjectAndDeletedAtIsNull(
                        OauthProvider.KAKAO,
                        "new-kakao-user")
                .orElseThrow();
        var wallet = creditWalletRepository.findByMemberId(member.getId()).orElseThrow();
        var session = authSessionRepository
                .findBySessionIdHash(sessionIdHasher.hash("new-service-session"))
                .orElseThrow();

        assertThat(result.memberStatus()).isEqualTo(member.getStatus());
        assertThat(member.getEmail()).isNull();
        assertThat(wallet.getCreditBalance()).isEqualTo(3);
        assertThat(creditTransactionRepository.findAll()).hasSize(1);
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plusHours(8));
    }

    @Test
    void existingMemberKeepsProfileAndOnlyNewestSessionRemainsUsable() {
        Member member = memberRepository.save(Member.register(
                OauthProvider.KAKAO,
                "existing-kakao-user",
                "기존 닉네임",
                "old@example.com",
                null,
                NOW.minusDays(1)));
        AuthSession oldSession = authSessionRepository.save(AuthSession.issue(
                member,
                sessionIdHasher.hash("old-service-session"),
                NOW.plusHours(1),
                NOW.minusMinutes(10)));
        entityManager.flush();
        entityManager.clear();

        transactionService.complete(
                new KakaoOauthUser(
                        "existing-kakao-user",
                        "변경된 닉네임",
                        "new@example.com",
                        "https://example.com/new.png"),
                "new-service-session");
        entityManager.flush();
        entityManager.clear();

        Member unchangedMember = memberRepository.findById(member.getId()).orElseThrow();
        AuthSession revokedSession = authSessionRepository.findById(oldSession.getId()).orElseThrow();
        assertThat(unchangedMember.getNickname()).isEqualTo("기존 닉네임");
        assertThat(unchangedMember.getEmail()).isEqualTo("old@example.com");
        assertThat(revokedSession.getRevokedAt()).isEqualTo(NOW);
        assertThat(authSessionRepository.findAllByMemberIdAndRevokedAtIsNullOrderByCreatedAtAsc(
                member.getId())).hasSize(1);
        assertThat(creditWalletRepository.findByMemberId(member.getId())).isEmpty();
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock fixedClock() {
            return Clock.fixed(CURRENT_INSTANT, ZoneOffset.UTC);
        }
    }
}
