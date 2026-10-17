package com.ktb10.kgb.common.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.credit.service.SignupCreditGrantService;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.event.NotificationCreatedEvent;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

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
@RecordApplicationEvents
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
    private NotificationRepository notificationRepository;

    @Autowired
    private CreditWalletRepository creditWalletRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    void cleanUp() {
        notificationRepository.deleteAll();
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

    @Test
    void existingMemberLoginKeepsUnreadNotificationWithoutReplayingPushEvent() {
        Member member = memberRepository.save(Member.register(
                OauthProvider.KAKAO,
                "notification-relogin-member",
                "기존 회원",
                null,
                null,
                NOW.minusDays(1)));
        notificationRepository.saveAndFlush(Notification.create(
                member,
                NotificationType.TRIP_STARTING,
                "여행 일정이 곧 시작돼요",
                "내일 시작하는 여행을 확인해 주세요.",
                NotificationReferenceType.GUIDEBOOK,
                "101",
                NOW.minusHours(1)));

        transactionService.complete(
                new KakaoOauthUser(
                        "notification-relogin-member",
                        "기존 회원",
                        null,
                        null),
                "relogin-service-session");

        assertThat(notificationRepository.findAll()).hasSize(1);
        assertThat(applicationEvents.stream(NotificationCreatedEvent.class)).isEmpty();
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock fixedClock() {
            return Clock.fixed(CURRENT_INSTANT, ZoneOffset.UTC);
        }
    }
}
