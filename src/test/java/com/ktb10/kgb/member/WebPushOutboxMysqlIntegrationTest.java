package com.ktb10.kgb.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.entity.WebPushOutbox;
import com.ktb10.kgb.member.entity.WebPushOutboxStatus;
import com.ktb10.kgb.member.entity.WebPushSubscription;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import com.ktb10.kgb.member.repository.WebPushOutboxRepository;
import com.ktb10.kgb.member.repository.WebPushSubscriptionRepository;
import com.ktb10.kgb.member.service.NotificationService;
import com.ktb10.kgb.member.service.WebPushOutboxWriter;
import com.ktb10.kgb.member.service.WebPushOutboxClaimService;
import com.ktb10.kgb.member.service.WebPushOutboxTask;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "webpush.outbox.worker.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
@Import(WebPushOutboxMysqlIntegrationTest.TransactionConfiguration.class)
class WebPushOutboxMysqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 12, 0);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withCommand("--log-bin-trust-function-creators=1");

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private WebPushOutboxRepository outboxRepository;

    @Autowired
    private WebPushSubscriptionRepository subscriptionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxTransaction outboxTransaction;

    @Autowired
    private WebPushOutboxClaimService claimService;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void cleanUp() {
        dropRejectTrigger();
        outboxRepository.deleteAllInBatch();
        subscriptionRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @AfterEach
    void dropTriggerAfterTest() {
        dropRejectTrigger();
    }

    @Test
    void storesPendingTaskWithNotificationInSameTransaction() throws Exception {
        Member member = activeMember("outbox-atomic-member", true);
        WebPushSubscription active = activeSubscription(member, 1, null);
        activeSubscription(member, 2, LocalDateTime.of(2000, 1, 1, 0, 0));

        notificationService.create(
                member.getId(),
                NotificationType.GUIDEBOOK_COMPLETED,
                "가이드북 생성 완료",
                "가이드북을 확인해 주세요.",
                NotificationReferenceType.GUIDEBOOK,
                "501");

        List<Notification> notifications = notificationRepository.findAll();
        List<WebPushOutbox> tasks = outboxRepository.findAll();
        assertThat(notifications).hasSize(1);
        assertThat(tasks).singleElement().satisfies(task -> {
            assertThat(task.getNotificationId()).isEqualTo(notifications.getFirst().getId());
            assertThat(task.getRecipientMemberId()).isEqualTo(member.getId());
            assertThat(task.getSubscriptionId()).isEqualTo(active.getId());
            assertThat(task.getStatus()).isEqualTo(WebPushOutboxStatus.PENDING);
            assertThat(task.getAttemptCount()).isZero();
            assertThat(task.getNextAttemptAt()).isEqualTo(task.getCreatedAt());
            assertThat(task.getSentAt()).isNull();
        });
        JsonNode payload = objectMapper.readTree(tasks.getFirst().getPayloadJson());
        assertThat(payload.get("notification_id").asText())
                .isEqualTo(notifications.getFirst().getId().toString());
        assertThat(payload.get("type").asText()).isEqualTo("GUIDEBOOK_COMPLETED");
        assertThat(payload.get("reference_type").asText()).isEqualTo("GUIDEBOOK");
        assertThat(payload.get("reference_id").asText()).isEqualTo("501");
    }

    @Test
    void skipsTaskWithoutRealtimeConsentOrActiveSubscription() {
        Member disabled = activeMember("outbox-disabled-member", false);
        activeSubscription(disabled, 3, null);
        Member noSubscription = activeMember("outbox-no-subscription-member", true);

        createNotification(disabled.getId(), "601");
        createNotification(noSubscription.getId(), "602");

        assertThat(notificationRepository.count()).isEqualTo(2);
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void rollsBackNotificationWhenOutboxInsertFails() {
        Member member = activeMember("outbox-rollback-member", true);
        activeSubscription(member, 4, null);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_web_push_outbox
                BEFORE INSERT ON web_push_outbox
                FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced outbox failure'
                """);

        assertThatThrownBy(() -> createNotification(member.getId(), "701"))
                .isInstanceOf(DataAccessException.class);

        assertThat(notificationRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void concurrentDuplicateRecordingKeepsOneTask() throws Exception {
        Member member = activeMember("outbox-concurrent-member", true);
        WebPushSubscription subscription = activeSubscription(member, 5, null);
        Notification notification = notificationRepository.saveAndFlush(Notification.create(
                member,
                NotificationType.GUIDEBOOK_COMPLETED,
                "가이드북 생성 완료",
                "가이드북을 확인해 주세요.",
                NotificationReferenceType.GUIDEBOOK,
                "801",
                NOW));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int request = 0; request < 2; request++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Outbox 동시 저장 시작 신호가 지연됐습니다.");
                    }
                    outboxTransaction.record(notification.getId(), NOW);
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }

        assertThat(outboxRepository.countByNotificationIdAndSubscriptionId(
                notification.getId(), subscription.getId())).isEqualTo(1);
    }

    @Test
    void concurrentWorkersClaimTaskOnlyOnce() throws Exception {
        Member member = activeMember("outbox-worker-member", true);
        activeSubscription(member, 6, null);
        createNotification(member.getId(), "901");
        jdbcTemplate.update(
                "UPDATE web_push_outbox SET next_attempt_at = '2000-01-01 00:00:00'");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<WebPushOutboxTask>>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int worker = 0; worker < 2; worker++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return claimService.claim(10, Duration.ofMinutes(1));
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int claimed = 0;
            for (Future<List<WebPushOutboxTask>> future : futures) {
                claimed += future.get(10, TimeUnit.SECONDS).size();
            }
            assertThat(claimed).isEqualTo(1);
        }
        assertThat(outboxRepository.findAll()).singleElement().satisfies(task -> {
            assertThat(task.getStatus()).isEqualTo(WebPushOutboxStatus.PROCESSING);
            assertThat(task.getAttemptCount()).isEqualTo(1);
            assertThat(task.getClaimToken()).isNotBlank();
        });
    }

    @Test
    void staleProcessingTaskCanBeClaimedAgain() {
        Member member = activeMember("outbox-recovery-member", true);
        activeSubscription(member, 7, null);
        createNotification(member.getId(), "902");
        Long taskId = outboxRepository.findAll().getFirst().getId();
        jdbcTemplate.update("""
                UPDATE web_push_outbox
                SET status = 'PROCESSING', attempt_count = 1,
                    claim_token = 'stale-token', processing_started_at = ?, updated_at = ?
                WHERE id = ?
                """, NOW.minusHours(1), NOW.minusHours(1), taskId);

        List<WebPushOutboxTask> reclaimed = claimService.claim(10, Duration.ofMinutes(1));

        assertThat(reclaimed).singleElement().satisfies(task -> {
            assertThat(task.id()).isEqualTo(taskId);
            assertThat(task.attemptCount()).isEqualTo(2);
            assertThat(task.claimToken()).isNotEqualTo("stale-token");
        });
    }

    private Member activeMember(String oauthSubject, boolean pushEnabled) {
        Member member = Member.register(
                OauthProvider.KAKAO,
                oauthSubject,
                "여행자",
                null,
                null,
                NOW.minusDays(1));
        member.activate(NOW.minusHours(1));
        if (!pushEnabled) {
            member.changePushEnabled(false, NOW.minusMinutes(30));
        }
        return memberRepository.saveAndFlush(member);
    }

    private WebPushSubscription activeSubscription(
            Member member,
            int sequence,
            LocalDateTime expirationAt) {
        byte[] endpointHash = new byte[32];
        endpointHash[31] = (byte) sequence;
        return subscriptionRepository.saveAndFlush(WebPushSubscription.register(
                member,
                Integer.toHexString(sequence).repeat(64),
                "https://fcm.googleapis.com/fcm/send/outbox-" + sequence,
                endpointHash,
                "p256dh-" + sequence,
                "auth-" + sequence,
                expirationAt,
                NOW.minusHours(1)));
    }

    private void createNotification(Long memberId, String referenceId) {
        notificationService.create(
                memberId,
                NotificationType.GUIDEBOOK_COMPLETED,
                "가이드북 생성 완료",
                "가이드북을 확인해 주세요.",
                NotificationReferenceType.GUIDEBOOK,
                referenceId);
    }

    private void dropRejectTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_web_push_outbox");
    }

    @TestConfiguration
    static class TransactionConfiguration {

        @Bean
        OutboxTransaction outboxTransaction(
                NotificationRepository notificationRepository,
                WebPushOutboxWriter outboxWriter) {
            return new OutboxTransaction(notificationRepository, outboxWriter);
        }
    }

    static class OutboxTransaction {

        private final NotificationRepository notificationRepository;
        private final WebPushOutboxWriter outboxWriter;

        OutboxTransaction(
                NotificationRepository notificationRepository,
                WebPushOutboxWriter outboxWriter) {
            this.notificationRepository = notificationRepository;
            this.outboxWriter = outboxWriter;
        }

        @Transactional
        public void record(Long notificationId, LocalDateTime now) {
            Notification notification = notificationRepository.findById(notificationId)
                    .orElseThrow();
            outboxWriter.recordPendingTasks(notification, true, now);
        }
    }
}
