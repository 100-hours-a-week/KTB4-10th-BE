package com.ktb10.kgb.member.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.common.security.oauth.KakaoOauthUser;
import com.ktb10.kgb.common.security.oauth.OauthLoginTransactionService;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.service.ServiceSessionService;
import com.ktb10.kgb.member.service.NotificationStreamService;
import com.ktb10.kgb.member.service.NotificationSseEmitterFactory;
import com.ktb10.kgb.member.service.NotificationRealtimePublisher;
import com.ktb10.kgb.support.RedisSessionTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.HexFormat;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:service-runtime;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class ServiceSessionRuntimeIntegrationTest extends RedisSessionTestSupport {
    @Autowired private ServiceSessionService service;
    @Autowired private ServiceSessionAuthenticationStore auth;
    @MockitoSpyBean private ServiceSessionLifecycleStore lifecycle;
    @Autowired private MemberRepository members;
    @Autowired private OauthLoginTransactionService login;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;
    @Autowired private NotificationStreamService streams;
    @MockitoSpyBean private NotificationRealtimePublisher publisher;
    @MockitoBean private NotificationSseEmitterFactory emitters;
    private final SessionIdHasher hasher = new SessionIdHasher();

    @Test
    void reloginClosesOnlyReplacedSessionEmittersAfterCommit() {
        Member member = member();
        String previousHash = HexFormat.of().formatHex(hasher.hash("previous"));
        String nextHash = HexFormat.of().formatHex(hasher.hash("next"));
        lifecycle.issueReplacingForMember(hasher.hash("previous"), member.getId(),
                Duration.ofHours(8), Duration.ofMinutes(30));
        SseEmitter previous = mock(SseEmitter.class);
        SseEmitter next = mock(SseEmitter.class);
        SseEmitter other = mock(SseEmitter.class);
        when(emitters.create(org.mockito.ArgumentMatchers.anyLong())).thenReturn(previous, next, other);
        streams.subscribe(member.getId(), previousHash);
        streams.subscribe(member.getId(), nextHash);
        streams.subscribe(member.getId() + 100000, previousHash);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            login.complete(new KakaoOauthUser(member.getOauthSubject(), "회원", null, null), "next");
            verify(previous, never()).complete();
            verify(publisher, never()).publishSessionDisconnected(member.getId(), previousHash);
        });
        verify(previous).complete();
        verify(next, never()).complete();
        verify(other, never()).complete();
        verify(publisher).publishSessionDisconnected(member.getId(), previousHash);
        assertThat(service.authenticate("previous")).isEmpty();
        assertThat(service.authenticate("next")).isPresent();
        streams.disconnectMember(member.getId());
        streams.disconnectMember(member.getId() + 100000);
    }

    @Test
    void authenticationExecutesOnlyOneMemberSelectAndNoMysqlUpdate() {
        Member member = member();
        lifecycle.issueReplacingForMember(hasher.hash("query-test"), member.getId(),
                Duration.ofHours(8), Duration.ofMinutes(30));
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        assertThat(service.authenticate("query-test")).isPresent();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityUpdateCount()).isZero();
        assertThat(statistics.getEntityInsertCount()).isZero();
    }

    @Test
    void onboardingCompletionIsVisibleWithoutUpdatingRedisMemberState() {
        Member member = member();
        lifecycle.issueReplacingForMember(hasher.hash("onboarding"), member.getId(),
                Duration.ofHours(8), Duration.ofMinutes(30));
        assertThat(service.authenticate("onboarding").orElseThrow().status()).isEqualTo(MemberStatus.ONBOARDING);
        member.activate(LocalDateTime.now(clock));
        members.saveAndFlush(member);
        assertThat(service.authenticate("onboarding").orElseThrow().status()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void dbRollbackRevokesTheNewRedisSession() {
        String subject = UUID.randomUUID().toString();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            login.complete(new KakaoOauthUser(subject, "가입", null, null), "rollback-token");
            assertThat(auth.authenticateAndRecordUse(hasher.hash("rollback-token"), Duration.ofMinutes(30)))
                    .isPresent();
            status.setRollbackOnly();
        });
        assertThat(members.findByOauthProviderAndOauthSubjectAndDeletedAtIsNull(OauthProvider.KAKAO, subject))
                .isEmpty();
        assertThat(auth.authenticateAndRecordUse(hasher.hash("rollback-token"), Duration.ofMinutes(30))).isEmpty();
    }

    @Test
    void deletedMemberIsRejectedEvenIfRedisSessionStillExists() {
        Member member = member();
        lifecycle.issueReplacingForMember(hasher.hash("deleted-member"), member.getId(),
                Duration.ofHours(8), Duration.ofMinutes(30));
        member.withdraw("withdrawn:" + member.getId(), LocalDateTime.now(clock));
        members.saveAndFlush(member);
        assertThat(auth.authenticateAndRecordUse(hasher.hash("deleted-member"), Duration.ofMinutes(30)))
                .contains(member.getId());
        assertThat(service.authenticate("deleted-member")).isEmpty();
    }

    @Test
    void redisIssuanceFailureRollsBackSignupInsteadOfReturningSuccessfulLogin() {
        String subject = UUID.randomUUID().toString();
        doThrow(new IllegalStateException("Redis down")).when(lifecycle)
                .issueReplacingForMember(any(byte[].class), anyLong(), any(Duration.class), any(Duration.class));
        assertThatThrownBy(() -> login.complete(new KakaoOauthUser(subject, "가입", null, null), "failed-token"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(members.findByOauthProviderAndOauthSubjectAndDeletedAtIsNull(OauthProvider.KAKAO, subject))
                .isEmpty();
    }

    private Member member() {
        return members.saveAndFlush(Member.register(OauthProvider.KAKAO, UUID.randomUUID().toString(),
                "회원", null, null, LocalDateTime.now(clock)));
    }
}
