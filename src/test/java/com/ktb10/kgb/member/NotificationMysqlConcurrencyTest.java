package com.ktb10.kgb.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import com.ktb10.kgb.member.service.NotificationService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class NotificationMysqlConcurrencyTest {

    private static final int MAX_NOTIFICATIONS = 20;
    private static final int CONCURRENT_REQUESTS = 4;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MemberRepository memberRepository;

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
        notificationRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void concurrentCreationKeepsTwentyNotificationsPerMember() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        Member member = Member.register(
                OauthProvider.KAKAO,
                "concurrent-notification-member",
                "여행자",
                null,
                null,
                now.minusDays(1));
        member.activate(now.minusHours(1));
        member = memberRepository.saveAndFlush(member);
        saveInitialNotifications(member, now);

        createConcurrently(member.getId());

        List<Notification> remaining = notificationRepository
                .findAllByRecipientIdOrderByCreatedAtDescIdDesc(
                        member.getId(),
                        PageRequest.of(0, MAX_NOTIFICATIONS))
                .getContent();
        assertThat(remaining).hasSize(MAX_NOTIFICATIONS);
        assertThat(remaining)
                .extracting(Notification::getReferenceId)
                .contains(
                        "concurrent-1",
                        "concurrent-2",
                        "concurrent-3",
                        "concurrent-4");
    }

    private void saveInitialNotifications(Member member, LocalDateTime now) {
        List<Notification> notifications = new ArrayList<>();
        for (int sequence = 1; sequence <= MAX_NOTIFICATIONS; sequence++) {
            notifications.add(Notification.create(
                    member,
                    NotificationType.GUIDEBOOK_COMPLETED,
                    "기존 알림 " + sequence,
                    "가이드북을 확인해 주세요.",
                    NotificationReferenceType.GUIDEBOOK,
                    "initial-" + sequence,
                    now.minusMinutes(MAX_NOTIFICATIONS - sequence + 1L)));
        }
        notificationRepository.saveAllAndFlush(notifications);
    }

    private void createConcurrently(Long memberId) throws Exception {
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int sequence = 1; sequence <= CONCURRENT_REQUESTS; sequence++) {
                int requestSequence = sequence;
                futures.add(executor.submit(() -> {
                    createAfterStart(memberId, requestSequence, ready, start);
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }
    }

    private void createAfterStart(
            Long memberId,
            int sequence,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 알림 생성 시작 신호를 기다리는 시간이 초과되었습니다.");
        }
        notificationService.create(
                memberId,
                NotificationType.GUIDEBOOK_COMPLETED,
                "동시 생성 알림 " + sequence,
                "가이드북을 확인해 주세요.",
                NotificationReferenceType.GUIDEBOOK,
                "concurrent-" + sequence);
    }
}
