package com.ktb10.kgb.common.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.credit.service.SignupCreditGrantService;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.service.NotificationRealtimePublisher;
import com.ktb10.kgb.member.service.NotificationStreamService;
import com.ktb10.kgb.member.session.ServiceSessionLifecycleStore;
import java.time.Clock;
import java.time.Duration;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;

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
    @MockitoBean private NotificationStreamService streams;
    @MockitoBean private NotificationRealtimePublisher publisher;

    @BeforeEach
    void defaultIssuanceHasNoPreviousSession() {
        when(sessions.issueReplacingForMember(any(byte[].class), anyLong(), any(Duration.class), any(Duration.class)))
                .thenReturn(new ServiceSessionLifecycleStore.IssueResult(NOW, NOW.plusSeconds(28800), Optional.empty()));
    }

    @Test
    void replacedSessionIsDisconnectedAfterCommittedTransactionCompletes() {
        String previous = "a".repeat(64);
        when(sessions.issueReplacingForMember(any(byte[].class), anyLong(), any(Duration.class), any(Duration.class)))
                .thenReturn(new ServiceSessionLifecycleStore.IssueResult(NOW, NOW.plusSeconds(28800),
                        Optional.of(previous)));
        service.complete(new KakaoOauthUser("replacement", "회원", null, null), "new-token");
        Long memberId = members.findByOauthProviderAndOauthSubjectAndDeletedAtIsNull(
                OauthProvider.KAKAO, "replacement").orElseThrow().getId();
        verifyNoInteractions(streams, publisher);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(streams).disconnectSession(memberId, previous);
        verify(publisher).publishSessionDisconnected(memberId, previous);
    }

    @Test
    void rollbackRevokesNewSessionAndDisconnectsPreviousSession() {
        when(sessions.issueReplacingForMember(any(byte[].class), anyLong(), any(Duration.class), any(Duration.class)))
                .thenReturn(new ServiceSessionLifecycleStore.IssueResult(NOW, NOW.plusSeconds(28800),
                        Optional.of("b".repeat(64))));
        service.complete(new KakaoOauthUser("rollback", "회원", null, null), "new-token");
        Long memberId = members.findByOauthProviderAndOauthSubjectAndDeletedAtIsNull(
                OauthProvider.KAKAO, "rollback").orElseThrow().getId();
        verifyNoInteractions(streams, publisher);
        TestTransaction.flagForRollback();
        TestTransaction.end();
        verify(streams).disconnectSession(memberId, "b".repeat(64));
        verify(publisher).publishSessionDisconnected(memberId, "b".repeat(64));
        verify(sessions).revokeCurrent(eq(new SessionIdHasher().hash("new-token")), anyLong());
    }

    @Test
    void rollbackCleanupFailureDoesNotSkipPreviousSessionDisconnect() {
        String previous = "d".repeat(64);
        when(sessions.issueReplacingForMember(any(byte[].class), anyLong(), any(Duration.class), any(Duration.class)))
                .thenReturn(new ServiceSessionLifecycleStore.IssueResult(NOW, NOW.plusSeconds(28800),
                        Optional.of(previous)));
        doThrow(new IllegalStateException("Redis unavailable")).when(sessions)
                .revokeCurrent(any(byte[].class), anyLong());
        service.complete(new KakaoOauthUser("cleanup-failure", "회원", null, null), "new-token");
        TestTransaction.flagForRollback();
        TestTransaction.end();
        verify(streams).disconnectSession(anyLong(), eq(previous));
        verify(publisher).publishSessionDisconnected(anyLong(), eq(previous));
    }

    @Test
    void firstLoginDoesNotPublishDisconnect() {
        service.complete(new KakaoOauthUser("first", "회원", null, null), "new-token");
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verifyNoInteractions(streams, publisher);
    }

    @Test
    void localDisconnectFailureStillPublishesAndDoesNotFailCommittedLogin() {
        when(sessions.issueReplacingForMember(any(byte[].class), anyLong(), any(Duration.class), any(Duration.class)))
                .thenReturn(new ServiceSessionLifecycleStore.IssueResult(NOW, NOW.plusSeconds(28800),
                        Optional.of("c".repeat(64))));
        doThrow(new IllegalStateException("local failure")).when(streams).disconnectSession(anyLong(), anyString());
        doThrow(new IllegalStateException("publish failure")).when(publisher)
                .publishSessionDisconnected(anyLong(), anyString());
        service.complete(new KakaoOauthUser("failure", "회원", null, null), "new-token");
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(publisher).publishSessionDisconnected(anyLong(), eq("c".repeat(64)));
    }

    @Test
    void newMemberAndCreditsAreStoredInMysqlWhileSessionIsIssuedInRedis() {
        long previousTransactions = transactions.count();
        var result = service.complete(new KakaoOauthUser("new", "여행자", null, null), "token");
        var member = members.findByOauthProviderAndOauthSubjectAndDeletedAtIsNull(
                OauthProvider.KAKAO, "new").orElseThrow();
        assertThat(result.memberStatus()).isEqualTo(member.getStatus());
        assertThat(wallets.findByMemberId(member.getId()).orElseThrow().getCreditBalance()).isEqualTo(3);
        assertThat(transactions.count()).isEqualTo(previousTransactions + 1);
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
        assertThat(wallets.findByMemberId(member.getId())).isEmpty();
        verify(sessions).issueReplacingForMember(any(byte[].class), eq(member.getId()),
                eq(Duration.ofHours(8)), eq(Duration.ofMinutes(30)));
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
