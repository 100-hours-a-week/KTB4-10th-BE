package com.ktb10.kgb.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.common.security.SessionIdHasher;
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
import java.util.Optional;
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
@Import({ServiceSessionService.class, SessionIdHasher.class,
        ServiceSessionServiceTest.FixedClockConfiguration.class})
class ServiceSessionServiceTest {

    private static final Instant CURRENT_INSTANT = Instant.parse("2026-09-17T05:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(CURRENT_INSTANT, ZoneOffset.UTC);

    @Autowired
    private ServiceSessionService serviceSessionService;

    @Autowired
    private SessionIdHasher sessionIdHasher;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void cleanUp() {
        authSessionRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void validSessionCreatesPrincipalAndUpdatesLastUsedAt() {
        Member member = memberRepository.save(member("valid-member"));
        AuthSession session = authSessionRepository.saveAndFlush(AuthSession.issue(
                member,
                sessionIdHasher.hash("valid-session"),
                NOW.plusHours(1),
                NOW.minusMinutes(10)));
        entityManager.clear();

        Optional<AuthenticatedMember> result = serviceSessionService.authenticate("valid-session");

        assertThat(result).contains(new AuthenticatedMember(
                member.getId(),
                member.getStatus(),
                session.getId()));
        AuthSession updatedSession = authSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(updatedSession.getLastUsedAt()).isEqualTo(NOW);
    }

    @Test
    void unknownRevokedAndExpiredSessionsAreRejected() {
        Member member = memberRepository.save(member("invalid-session-member"));
        AuthSession revokedSession = AuthSession.issue(
                member,
                sessionIdHasher.hash("revoked-session"),
                NOW.plusHours(1),
                NOW.minusMinutes(10));
        revokedSession.revoke(NOW.minusMinutes(1));
        AuthSession expiredSession = AuthSession.issue(
                member,
                sessionIdHasher.hash("expired-session"),
                NOW,
                NOW.minusHours(1));
        AuthSession idleExpiredSession = AuthSession.issue(
                member,
                sessionIdHasher.hash("idle-expired-session"),
                NOW.plusHours(1),
                NOW.minusMinutes(30));
        authSessionRepository.saveAllAndFlush(
                java.util.List.of(revokedSession, expiredSession, idleExpiredSession));
        entityManager.clear();

        assertThat(serviceSessionService.authenticate("unknown-session")).isEmpty();
        assertThat(serviceSessionService.authenticate("revoked-session")).isEmpty();
        assertThat(serviceSessionService.authenticate("expired-session")).isEmpty();
        assertThat(serviceSessionService.authenticate("idle-expired-session")).isEmpty();
    }

    @Test
    void withdrawnMemberSessionIsRejected() {
        Member member = memberRepository.save(member("withdrawn-member"));
        AuthSession session = authSessionRepository.save(AuthSession.issue(
                member,
                sessionIdHasher.hash("withdrawn-session"),
                NOW.plusHours(1),
                NOW.minusMinutes(10)));
        member.withdraw("withdrawn:" + member.getId(), NOW.minusMinutes(1));
        memberRepository.flush();
        authSessionRepository.flush();
        entityManager.clear();

        assertThat(serviceSessionService.authenticate("withdrawn-session")).isEmpty();
        assertThat(authSessionRepository.findById(session.getId())).isPresent();
    }

    @Test
    void nullLastUsedAtFallsBackToCreatedAt() {
        Member member = memberRepository.save(member("legacy-session-member"));
        AuthSession session = authSessionRepository.saveAndFlush(AuthSession.issue(
                member,
                sessionIdHasher.hash("legacy-session"),
                NOW.plusHours(1),
                NOW.minusMinutes(20)));
        entityManager.createNativeQuery("update auth_sessions set last_used_at = null where id = :id")
                .setParameter("id", session.getId())
                .executeUpdate();
        entityManager.clear();

        assertThat(serviceSessionService.authenticate("legacy-session")).isPresent();
        assertThat(authSessionRepository.findById(session.getId()).orElseThrow().getLastUsedAt())
                .isEqualTo(NOW);
    }

    private static Member member(String oauthSubject) {
        return Member.register(
                OauthProvider.KAKAO,
                oauthSubject,
                "여행자",
                null,
                null,
                NOW.minusDays(1));
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock fixedClock() {
            return Clock.fixed(CURRENT_INSTANT, ZoneOffset.UTC);
        }
    }
}
