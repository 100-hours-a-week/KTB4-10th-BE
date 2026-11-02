package com.ktb10.kgb.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.content.event.InterestedContentChangesDetectedEvent;
import com.ktb10.kgb.content.service.InterestedContentChangeNotificationProcessor;
import com.ktb10.kgb.member.repository.NotificationOccurrenceRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers(disabledWithoutDocker = true)
class InterestedContentChangeNotificationMysqlIntegrationTest {

    private static final LocalDate SYNC_DATE = LocalDate.of(2026, 10, 30);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 0, 0);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private InterestedContentChangeNotificationProcessor processor;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationOccurrenceRepository notificationOccurrenceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM web_push_outbox");
        notificationOccurrenceRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM favorite_contents");
        jdbcTemplate.update("DELETE FROM tour_api_content_change_occurrences");
        jdbcTemplate.update("DELETE FROM tour_api_sync_runs");
        jdbcTemplate.update("DELETE FROM event_details");
        jdbcTemplate.update("DELETE FROM tourism_contents");
        jdbcTemplate.update("DELETE FROM members");
        jdbcTemplate.update("DELETE FROM regions");
    }

    @Test
    void concurrentReprocessingCreatesOneNotificationPerMemberAndOccurrence() throws Exception {
        UUID runId = insertRun();
        Long memberId = insertMember("event-change-concurrent", "ACTIVE");
        Long contentId = insertEvent("event-100", "서울 불꽃축제");
        insertFavorite(memberId, contentId);
        insertOccurrence(runId, "event-100", "[\"DESCRIPTION\",\"START_DATE\"]");
        InterestedContentChangesDetectedEvent event = event(runId);

        List<InterestedContentChangeNotificationProcessor.ProcessingResult> results =
                runConcurrently(event, 2);

        assertThat(notificationRepository.count()).isOne();
        assertThat(notificationOccurrenceRepository.count()).isOne();
        assertThat(results)
                .extracting(InterestedContentChangeNotificationProcessor.ProcessingResult::created)
                .containsExactlyInAnyOrder(1, 0);
        assertThat(results)
                .extracting(InterestedContentChangeNotificationProcessor.ProcessingResult::duplicate)
                .containsExactlyInAnyOrder(0, 1);

        notificationRepository.deleteAllInBatch();
        var retried = processor.process(event);

        assertThat(retried.created()).isZero();
        assertThat(retried.duplicate()).isOne();
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void excludesRemovedFavoriteAndInactiveMemberAtProcessingTime() {
        UUID runId = insertRun();
        Long removedMemberId = insertMember("event-change-removed", "ACTIVE");
        Long inactiveMemberId = insertMember("event-change-inactive", "ONBOARDING");
        Long contentId = insertEvent("event-200", "가을 음악회");
        insertFavorite(removedMemberId, contentId);
        insertFavorite(inactiveMemberId, contentId);
        jdbcTemplate.update(
                "DELETE FROM favorite_contents WHERE member_id = ? AND content_id = ?",
                removedMemberId,
                contentId);
        insertOccurrence(runId, "event-200", "[\"END_DATE\"]");

        var result = processor.process(event(runId));

        assertThat(result.targets()).isZero();
        assertThat(notificationRepository.count()).isZero();
        assertThat(notificationOccurrenceRepository.count()).isZero();
    }

    private List<InterestedContentChangeNotificationProcessor.ProcessingResult> runConcurrently(
            InterestedContentChangesDetectedEvent event,
            int instanceCount) throws Exception {
        CountDownLatch ready = new CountDownLatch(instanceCount);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<InterestedContentChangeNotificationProcessor.ProcessingResult>> futures =
                    new ArrayList<>();
            for (int index = 0; index < instanceCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 재처리 시작 신호가 지연됐습니다.");
                    }
                    return processor.process(event);
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<InterestedContentChangeNotificationProcessor.ProcessingResult> results =
                    new ArrayList<>();
            for (Future<InterestedContentChangeNotificationProcessor.ProcessingResult> future
                    : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private UUID insertRun() {
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tour_api_sync_runs (
                    run_id, sync_date, status, attempt_count,
                    started_at, completed_at, created_at, updated_at
                ) VALUES (?, ?, 'SUCCESS', 1, ?, ?, ?, ?)
                """,
                runId.toString(),
                SYNC_DATE,
                NOW,
                NOW,
                NOW,
                NOW);
        return runId;
    }

    private Long insertMember(String oauthSubject, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    oauth_provider, oauth_subject, nickname, language_code,
                    status, push_enabled, created_at, updated_at
                ) VALUES ('KAKAO', ?, '테스트회원', 'ko', ?, FALSE, ?, ?)
                """,
                oauthSubject,
                status,
                NOW,
                NOW);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE oauth_subject = ?",
                Long.class,
                oauthSubject);
    }

    private Long insertEvent(String sourceContentId, String title) {
        jdbcTemplate.update(
                """
                INSERT INTO regions (administrative_code, name, region_level)
                VALUES ('11', '서울특별시', 'PROVINCE')
                ON DUPLICATE KEY UPDATE name = VALUES(name)
                """);
        Long regionId = jdbcTemplate.queryForObject(
                "SELECT id FROM regions WHERE administrative_code = '11'",
                Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO tourism_contents (
                    source_provider, source_content_id, title, region_id,
                    classification_code_1, address, location,
                    status, created_at, updated_at
                ) VALUES (
                    'TOUR_API', ?, ?, ?, 'EV', '서울특별시',
                    ST_SRID(POINT(126.9780, 37.5665), 4326),
                    'ACTIVE', ?, ?
                )
                """,
                sourceContentId,
                title,
                regionId,
                NOW,
                NOW);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tourism_contents WHERE source_content_id = ?",
                Long.class,
                sourceContentId);
    }

    private void insertFavorite(Long memberId, Long contentId) {
        jdbcTemplate.update(
                "INSERT INTO favorite_contents (member_id, content_id, created_at) VALUES (?, ?, ?)",
                memberId,
                contentId,
                NOW);
    }

    private void insertOccurrence(UUID runId, String sourceContentId, String changedFieldsJson) {
        jdbcTemplate.update(
                """
                INSERT INTO tour_api_content_change_occurrences (
                    run_id, source_provider, source_content_id,
                    lifecycle_change, changed_fields_json, created_at, updated_at
                ) VALUES (?, 'TOUR_API', ?, 'NONE', ?, ?, ?)
                """,
                runId.toString(),
                sourceContentId,
                changedFieldsJson,
                NOW,
                NOW);
    }

    private InterestedContentChangesDetectedEvent event(UUID runId) {
        return new InterestedContentChangesDetectedEvent(runId, SYNC_DATE, 1);
    }
}
