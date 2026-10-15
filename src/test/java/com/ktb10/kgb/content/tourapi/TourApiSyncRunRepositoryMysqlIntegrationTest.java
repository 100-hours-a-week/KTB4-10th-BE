package com.ktb10.kgb.content.tourapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
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
        "spring.jpa.hibernate.ddl-auto=validate",
        "tour-api.scheduler.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class TourApiSyncRunRepositoryMysqlIntegrationTest {

    private static final LocalDate SYNC_DATE = LocalDate.of(2026, 10, 12);
    private static final Instant NOW = Instant.parse("2026-10-12T01:00:00Z");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private TourApiSyncRunRepository repository;

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
        jdbcTemplate.update("DELETE FROM tour_api_sync_runs");
    }

    @Test
    void onlyOneConcurrentRunOwnsTheSameSyncDate() throws Exception {
        UUID firstRunId = UUID.randomUUID();
        UUID secondRunId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> tryStartAfterSignal(firstRunId, ready, start)),
                    executor.submit(() -> tryStartAfterSignal(secondRunId, ready, start)));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    results.get(0).get(10, TimeUnit.SECONDS),
                    results.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(runCount()).isOne();
    }

    @Test
    void activeRunAndSuccessfulRunCannotBeTakenOver() {
        UUID owner = UUID.randomUUID();
        assertThat(repository.tryStart(
                owner, SYNC_DATE, NOW, NOW.minusSeconds(7_200))).isTrue();

        assertThat(repository.tryStart(
                UUID.randomUUID(), SYNC_DATE, NOW.plusSeconds(60), NOW.minusSeconds(7_140)))
                .isFalse();

        repository.complete(owner, emptySummary(), NOW.plusSeconds(120));

        assertThat(repository.tryStart(
                UUID.randomUUID(), SYNC_DATE, NOW.plusSeconds(10_000), NOW.plusSeconds(2_800)))
                .isFalse();
    }

    @Test
    void staleRunningRunIsTakenOverAndReset() {
        UUID staleRunId = UUID.randomUUID();
        UUID takeoverRunId = UUID.randomUUID();
        assertThat(repository.tryStart(
                staleRunId, SYNC_DATE, NOW.minusSeconds(10_000), NOW.minusSeconds(17_200)))
                .isTrue();
        repository.recordAttempt(staleRunId, 2, NOW.minusSeconds(9_900));

        assertThat(repository.tryStart(
                takeoverRunId, SYNC_DATE, NOW, NOW.minusSeconds(7_200))).isTrue();

        RunState state = jdbcTemplate.queryForObject(
                """
                SELECT run_id, status, attempt_count, completed_at, failure_stage
                FROM tour_api_sync_runs
                WHERE sync_date = ?
                """,
                (resultSet, rowNumber) -> new RunState(
                        resultSet.getString("run_id"),
                        resultSet.getString("status"),
                        resultSet.getInt("attempt_count"),
                        resultSet.getTimestamp("completed_at") != null,
                        resultSet.getString("failure_stage")),
                SYNC_DATE);
        assertThat(state).isEqualTo(new RunState(
                takeoverRunId.toString(), "RUNNING", 0, false, null));
    }

    @Test
    void failedRunCanBeStartedAgain() {
        UUID failedRunId = UUID.randomUUID();
        UUID retryRunId = UUID.randomUUID();
        assertThat(repository.tryStart(
                failedRunId, SYNC_DATE, NOW, NOW.minusSeconds(7_200))).isTrue();
        repository.fail(failedRunId, "SYNCHRONIZE", new IllegalStateException("failure"), NOW);

        assertThat(repository.tryStart(
                retryRunId, SYNC_DATE, NOW.plusSeconds(60), NOW.minusSeconds(7_140))).isTrue();
    }

    private boolean tryStartAfterSignal(
            UUID runId,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 잠금 검증 시작 신호를 기다리는 시간이 초과되었습니다.");
        }
        return repository.tryStart(runId, SYNC_DATE, NOW, NOW.minusSeconds(7_200));
    }

    private int runCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tour_api_sync_runs WHERE sync_date = ?",
                Integer.class,
                SYNC_DATE);
        return count == null ? 0 : count;
    }

    private ImportSummary emptySummary() {
        return new ImportSummary(0, 0, 0, 0, 0, 0, 0, 0);
    }

    private record RunState(
            String runId,
            String status,
            int attemptCount,
            boolean completed,
            String failureStage) {
    }
}
