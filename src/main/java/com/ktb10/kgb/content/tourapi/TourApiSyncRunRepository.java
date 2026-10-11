package com.ktb10.kgb.content.tourapi;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 기준일별 실행 이력 행을 분산 잠금으로 사용합니다. */
@Repository
public class TourApiSyncRunRepository {

    private final JdbcTemplate jdbcTemplate;

    public TourApiSyncRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryStart(
            UUID runId,
            LocalDate syncDate,
            Instant now,
            Instant staleBefore) {
        String id = runId.toString();
        Timestamp startedAt = Timestamp.from(now);
        jdbcTemplate.update(
                """
                INSERT INTO tour_api_sync_runs (
                    run_id, sync_date, status, attempt_count,
                    started_at, completed_at, failure_stage, failure_message,
                    created_at, updated_at
                ) VALUES (?, ?, 'RUNNING', 0, ?, NULL, NULL, NULL, ?, ?)
                ON DUPLICATE KEY UPDATE
                    run_id = IF(
                        status = 'SUCCESS'
                        OR (status = 'RUNNING' AND started_at >= ?),
                        run_id,
                        VALUES(run_id)),
                    status = IF(run_id = VALUES(run_id), 'RUNNING', status),
                    attempt_count = IF(run_id = VALUES(run_id), 0, attempt_count),
                    started_at = IF(run_id = VALUES(run_id), VALUES(started_at), started_at),
                    completed_at = IF(run_id = VALUES(run_id), NULL, completed_at),
                    failure_stage = IF(run_id = VALUES(run_id), NULL, failure_stage),
                    failure_message = IF(run_id = VALUES(run_id), NULL, failure_message),
                    updated_at = IF(run_id = VALUES(run_id), VALUES(updated_at), updated_at)
                """,
                id,
                syncDate,
                startedAt,
                startedAt,
                startedAt,
                Timestamp.from(staleBefore));
        Integer owned = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tour_api_sync_runs
                WHERE sync_date = ? AND run_id = ? AND status = 'RUNNING'
                """,
                Integer.class,
                syncDate,
                id);
        return owned != null && owned == 1;
    }

    public void recordAttempt(UUID runId, int attempt, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE tour_api_sync_runs
                SET attempt_count = ?, updated_at = ?
                WHERE run_id = ? AND status = 'RUNNING'
                """,
                attempt,
                Timestamp.from(now),
                runId.toString());
    }

    public void complete(UUID runId, ImportSummary summary, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE tour_api_sync_runs
                SET status = 'SUCCESS',
                    source_contents = ?, imported_contents = ?,
                    inactive_contents = ?, reactivated_contents = ?,
                    source_events = ?, imported_events = ?,
                    completed_at = ?, updated_at = ?
                WHERE run_id = ? AND status = 'RUNNING'
                """,
                summary.sourceContents(),
                summary.importedContents(),
                summary.inactiveContents(),
                summary.reactivatedContents(),
                summary.sourceEvents(),
                summary.importedEvents(),
                Timestamp.from(now),
                Timestamp.from(now),
                runId.toString());
    }

    public void fail(UUID runId, String failureStage, Throwable failure, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE tour_api_sync_runs
                SET status = 'FAILED', failure_stage = ?, failure_message = ?,
                    completed_at = ?, updated_at = ?
                WHERE run_id = ? AND status = 'RUNNING'
                """,
                failureStage,
                limit(failure.getClass().getSimpleName() + ": "
                        + String.valueOf(failure.getMessage()), 1000),
                Timestamp.from(now),
                Timestamp.from(now),
                runId.toString());
    }

    private static String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
