package com.ktb10.kgb.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.member.dto.request.WebPushSubscriptionKeysRequest;
import com.ktb10.kgb.member.dto.request.WebPushSubscriptionRequest;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.entity.WebPushSubscriptionStatus;
import com.ktb10.kgb.member.error.MemberErrorCode;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.WebPushSubscriptionRepository;
import com.ktb10.kgb.member.service.WebPushSubscriptionService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class WebPushSubscriptionMysqlConcurrencyTest extends com.ktb10.kgb.support.RedisSessionTestSupport {

    private static final int CONCURRENT_REQUESTS = 6;
    private static final String P256DH =
            "BAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String AUTH = "AAAAAAAAAAAAAAAAAAAAAA";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private WebPushSubscriptionService subscriptionService;

    @Autowired
    private WebPushSubscriptionRepository subscriptionRepository;


    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SessionIdHasher sessionIdHasher;

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
        subscriptionRepository.deleteAllInBatch();
        sessions.clear();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void concurrentRegistrationKeepsFiveActiveSubscriptionsPerMember() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        Member member = Member.register(
                OauthProvider.KAKAO,
                "concurrent-web-push-member",
                "여행자",
                null,
                null,
                now.minusDays(1));
        member.activate(now.minusHours(1));
        member = memberRepository.saveAndFlush(member);
        String session = sessions.seed(
                member,
                sessionIdHasher.hash("concurrent-web-push-session"),
                now.plusHours(8),
                now.minusMinutes(1));

        List<Boolean> results = registerConcurrently(member.getId(), session);

        assertThat(results).containsExactlyInAnyOrder(true, true, true, true, true, false);
        assertThat(subscriptionRepository.countByMemberIdAndStatus(
                        member.getId(), WebPushSubscriptionStatus.ACTIVE))
                .isEqualTo(5);
    }

    private List<Boolean> registerConcurrently(Long memberId, String sessionHash) throws Exception {
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int sequence = 1; sequence <= CONCURRENT_REQUESTS; sequence++) {
                int requestSequence = sequence;
                futures.add(executor.submit(() -> registerAfterStart(
                        memberId, sessionHash, requestSequence, ready, start)));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private boolean registerAfterStart(
            Long memberId,
            String sessionHash,
            int sequence,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 구독 등록 시작 신호를 기다리는 시간이 초과되었습니다.");
        }
        try {
            subscriptionService.registerOrRenew(
                    memberId,
                    sessionHash,
                    new WebPushSubscriptionRequest(
                            "https://fcm.googleapis.com/fcm/send/concurrent-" + sequence,
                            null,
                            new WebPushSubscriptionKeysRequest(P256DH, AUTH)));
            return true;
        } catch (BusinessException exception) {
            assertThat(exception.errorCode())
                    .isEqualTo(MemberErrorCode.WEB_PUSH_SUBSCRIPTION_LIMIT_EXCEEDED);
            return false;
        }
    }
}
