package com.ktb10.kgb.common.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.credit.entity.CreditTransactionType;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OauthLoginMysqlConcurrencyTest {

    private static final int CONCURRENT_REQUESTS = 2;
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private OauthLoginService oauthLoginService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private CreditWalletRepository creditWalletRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @Autowired
    private Clock clock;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void cleanUp() {
        creditTransactionRepository.deleteAllInBatch();
        creditWalletRepository.deleteAllInBatch();
        authSessionRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void concurrentSignupCreatesOneMemberWalletGrantAndActiveSession() throws Exception {
        KakaoOauthUser user = new KakaoOauthUser(
                "concurrent-new-member",
                "동시 가입자",
                null,
                null);

        List<OauthLoginResult> results = loginConcurrently(user);

        assertThat(results).hasSize(CONCURRENT_REQUESTS);
        assertThat(memberRepository.count()).isOne();
        Member member = memberRepository.findByOauthProviderAndOauthSubjectAndDeletedAtIsNull(
                        OauthProvider.KAKAO,
                        user.subject())
                .orElseThrow();
        assertThat(creditWalletRepository.findByMemberId(member.getId()).orElseThrow()
                .getCreditBalance()).isEqualTo(3);
        assertThat(creditTransactionRepository.findAll())
                .singleElement()
                .extracting(transaction -> transaction.getType())
                .isEqualTo(CreditTransactionType.FREE_GRANT);
        assertThat(authSessionRepository.findAllByMemberIdAndRevokedAtIsNullOrderByCreatedAtAsc(
                member.getId())).hasSize(1);
    }

    @Test
    void concurrentExistingMemberLoginLeavesOneActiveSession() throws Exception {
        KakaoOauthUser user = new KakaoOauthUser(
                "concurrent-existing-member",
                "기존 회원",
                "existing@example.com",
                null);
        Member member = memberRepository.saveAndFlush(Member.register(
                OauthProvider.KAKAO,
                user.subject(),
                user.nickname(),
                user.email(),
                user.profileImageUrl(),
                LocalDateTime.now(clock)));

        List<OauthLoginResult> results = loginConcurrently(user);

        assertThat(results).hasSize(CONCURRENT_REQUESTS);
        assertThat(memberRepository.count()).isOne();
        assertThat(authSessionRepository.findAllByMemberIdAndRevokedAtIsNullOrderByCreatedAtAsc(
                member.getId())).hasSize(1);
    }

    private List<OauthLoginResult> loginConcurrently(KakaoOauthUser user)
            throws InterruptedException, ExecutionException, TimeoutException {
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<OauthLoginResult> first = executor.submit(() -> loginAfterStart(user, ready, start));
            Future<OauthLoginResult> second = executor.submit(() -> loginAfterStart(user, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));
        }
    }

    private OauthLoginResult loginAfterStart(
            KakaoOauthUser user,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 로그인 시작 신호를 기다리는 시간이 초과되었습니다.");
        }
        return oauthLoginService.login(user);
    }
}
