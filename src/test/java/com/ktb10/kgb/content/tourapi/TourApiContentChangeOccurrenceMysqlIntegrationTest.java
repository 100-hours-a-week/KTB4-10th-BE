package com.ktb10.kgb.content.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TourApiContentChangeOccurrenceMysqlIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private TourApiContentChangeOccurrenceRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        jdbcTemplate.update("DELETE FROM tour_api_content_change_occurrences");
        jdbcTemplate.update("DELETE FROM tour_api_sync_runs");
    }

    @Test
    void mergesBaseAndDetailChangesIntoOneOccurrence() {
        UUID runId = insertRun(LocalDate.of(2026, 10, 30));
        ContentChangeSet baseChange = ContentChangeSet.of(
                "content-1",
                Set.of(ContentChangedField.TITLE, ContentChangedField.LOCATION),
                ContentLifecycleChange.NEW,
                new TourApiContentLocation(37.1, 127.1),
                new TourApiContentLocation(37.2, 127.2));
        ContentChangeSet detailChange = ContentChangeSet.of(
                "content-1",
                Set.of(ContentChangedField.DESCRIPTION, ContentChangedField.START_DATE),
                ContentLifecycleChange.NONE,
                null,
                new TourApiContentLocation(37.2, 127.2));

        transactionTemplate.executeWithoutResult(status -> {
            repository.merge(runId, List.of(baseChange));
            repository.merge(runId, List.of(detailChange));
        });

        List<TourApiContentChangeOccurrence> occurrences = repository.findAllByRunId(runId);
        assertThat(occurrences).hasSize(1);
        TourApiContentChangeOccurrence occurrence = occurrences.getFirst();
        assertThat(occurrence.lifecycleChange()).isEqualTo(ContentLifecycleChange.NEW);
        assertThat(occurrence.changedFields()).containsExactlyInAnyOrder(
                ContentChangedField.TITLE,
                ContentChangedField.LOCATION,
                ContentChangedField.DESCRIPTION,
                ContentChangedField.START_DATE);
        assertThat(occurrence.previousLocation())
                .isEqualTo(new TourApiContentLocation(37.1, 127.1));
        assertThat(occurrence.currentLocation())
                .isEqualTo(new TourApiContentLocation(37.2, 127.2));
    }

    @Test
    void storesDifferentContentsAsDifferentOccurrences() {
        UUID runId = insertRun(LocalDate.of(2026, 10, 30));

        transactionTemplate.executeWithoutResult(status -> repository.merge(
                runId,
                List.of(
                        change("content-1", ContentChangedField.TITLE),
                        change("content-2", ContentChangedField.END_DATE))));

        assertThat(repository.findAllByRunId(runId))
                .extracting(TourApiContentChangeOccurrence::sourceContentId)
                .containsExactly("content-1", "content-2");
    }

    @Test
    void storesInactiveLifecycleChange() {
        UUID runId = insertRun(LocalDate.of(2026, 10, 30));
        ContentChangeSet inactiveChange = ContentChangeSet.of(
                "content-1",
                Set.of(ContentChangedField.STATUS),
                ContentLifecycleChange.INACTIVATED,
                null,
                null);

        transactionTemplate.executeWithoutResult(status ->
                repository.merge(runId, List.of(inactiveChange)));

        assertThat(repository.findAllByRunId(runId).getFirst().lifecycleChange())
                .isEqualTo(ContentLifecycleChange.INACTIVATED);
    }

    @Test
    void ignoresChangeSetWithoutMeaningfulChanges() {
        UUID runId = insertRun(LocalDate.of(2026, 10, 30));

        transactionTemplate.executeWithoutResult(status -> repository.merge(
                runId,
                List.of(ContentChangeSet.of(
                        "content-1",
                        Set.of(),
                        ContentLifecycleChange.NONE,
                        null,
                        null))));

        assertThat(repository.findAllByRunId(runId)).isEmpty();
    }

    @Test
    void rollsBackOccurrenceWithSynchronizationTransaction() {
        UUID runId = insertRun(LocalDate.of(2026, 10, 30));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            repository.merge(runId, List.of(change("content-1", ContentChangedField.TITLE)));
            throw new IllegalStateException("동기화 트랜잭션 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(repository.findAllByRunId(runId)).isEmpty();
    }

    @Test
    void rejectsOccurrenceWithoutSynchronizationRun() {
        UUID missingRunId = UUID.randomUUID();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                repository.merge(
                        missingRunId,
                        List.of(change("content-1", ContentChangedField.TITLE)))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertRun(LocalDate syncDate) {
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tour_api_sync_runs (
                    run_id, sync_date, status, attempt_count,
                    started_at, created_at, updated_at
                ) VALUES (?, ?, 'RUNNING', 1, NOW(6), NOW(6), NOW(6))
                """,
                runId.toString(),
                syncDate);
        return runId;
    }

    private static ContentChangeSet change(
            String sourceContentId,
            ContentChangedField changedField) {
        return ContentChangeSet.of(
                sourceContentId,
                Set.of(changedField),
                ContentLifecycleChange.NONE,
                null,
                null);
    }
}
