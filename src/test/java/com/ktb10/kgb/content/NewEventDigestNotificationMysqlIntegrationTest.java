package com.ktb10.kgb.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ktb10.kgb.content.repository.NewEventQuery;
import com.ktb10.kgb.content.service.NewEventDigestNotificationService;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationOccurrenceRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import java.time.LocalDate;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class NewEventDigestNotificationMysqlIntegrationTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 11, 2);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 9, 0);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private NewEventDigestNotificationService notificationService;

    @Autowired
    private NotificationOccurrenceRepository occurrenceRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private NewEventQuery newEventQuery;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void cleanUp() {
        occurrenceRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
        given(newEventQuery.countByCreatedAt(
                REFERENCE_DATE.atStartOfDay(),
                REFERENCE_DATE.plusDays(1).atStartOfDay()))
                .willReturn(2L);
    }

    @Test
    void concurrentInstancesCreateOneNotificationOnlyForActiveMember() throws Exception {
        Member active = activeMember("new-event-active");
        memberRepository.saveAndFlush(Member.register(
                OauthProvider.KAKAO,
                "new-event-onboarding",
                "온보딩 회원",
                null,
                null,
                NOW));

        runConcurrently(2);

        assertThat(notificationRepository.findAll())
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getRecipient().getId()).isEqualTo(active.getId());
                    assertThat(notification.getType())
                            .isEqualTo(NotificationType.NEW_EVENTS_ADDED);
                    assertThat(notification.getReferenceType())
                            .isEqualTo(NotificationReferenceType.EVENT_LIST);
                    assertThat(notification.getReferenceId()).isEqualTo("2026-11-02");
                });
        assertThat(occurrenceRepository.count()).isOne();

        notificationRepository.deleteAllInBatch();
        notificationService.createForReferenceDate(REFERENCE_DATE);

        assertThat(notificationRepository.count()).isZero();
        assertThat(occurrenceRepository.count()).isOne();
    }

    private Member activeMember(String oauthSubject) {
        Member member = Member.register(
                OauthProvider.KAKAO,
                oauthSubject,
                "활성 회원",
                null,
                null,
                NOW.minusDays(1));
        member.activate(NOW);
        return memberRepository.saveAndFlush(member);
    }

    private void runConcurrently(int instanceCount) throws Exception {
        CountDownLatch ready = new CountDownLatch(instanceCount);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < instanceCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "동시 실행 시작 신호를 기다리는 시간이 초과되었습니다.");
                    }
                    notificationService.createForReferenceDate(REFERENCE_DATE);
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        }
    }
}
