package com.ktb10.kgb.content.tourapi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 운영 DB의 활성 TourAPI 콘텐츠를 ID 커서 기반 배치로 상세 재적재합니다. */
@Service
public class TourApiDetailBackfillService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TourApiDetailBackfillService.class);

    private final JdbcTemplate jdbcTemplate;
    private final TourApiDetailSyncService detailSyncService;

    public TourApiDetailBackfillService(
            JdbcTemplate jdbcTemplate,
            TourApiDetailSyncService detailSyncService) {
        this.jdbcTemplate = jdbcTemplate;
        this.detailSyncService = detailSyncService;
    }

    public BackfillBatchResult runBatch(long afterId, int batchSize, Duration requestDelay) {
        List<TourApiDetailTarget> targets = findTargets(afterId, batchSize);
        if (targets.isEmpty()) {
            return new BackfillBatchResult(afterId, afterId, 0, 0, List.of(), false);
        }

        String runId = "backfill-" + UUID.randomUUID();
        int completed = 0;
        List<String> failedSourceContentIds = new ArrayList<>();
        for (int index = 0; index < targets.size(); index++) {
            TourApiDetailTarget target = targets.get(index);
            try {
                detailSyncService.synchronizeTarget(runId, target);
                completed++;
            } catch (RuntimeException failure) {
                failedSourceContentIds.add(target.sourceContentId());
                LOGGER.error(
                        "TourAPI detail backfill target failed: runId={}, internalId={}, contentId={}",
                        runId,
                        target.id(),
                        target.sourceContentId(),
                        failure);
            }
            if (index + 1 < targets.size()) {
                waitBeforeNextRequest(requestDelay);
            }
        }

        long nextAfterId = targets.getLast().id();
        boolean hasMore = hasTargetAfter(nextAfterId);
        LOGGER.info(
                "TourAPI detail backfill batch completed: runId={}, afterId={}, nextAfterId={}, "
                        + "targets={}, completed={}, failed={}, hasMore={}",
                runId,
                afterId,
                nextAfterId,
                targets.size(),
                completed,
                failedSourceContentIds.size(),
                hasMore);
        return new BackfillBatchResult(
                afterId,
                nextAfterId,
                targets.size(),
                completed,
                failedSourceContentIds,
                hasMore);
    }

    private List<TourApiDetailTarget> findTargets(long afterId, int batchSize) {
        return jdbcTemplate.query(
                """
                SELECT id, source_content_id, source_content_type_id, classification_code_1
                FROM tourism_contents
                WHERE source_provider = 'TOUR_API'
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
                  AND classification_code_1 IN ('NA', 'HS', 'VE', 'EX', 'LS', 'EV')
                  AND id > ?
                ORDER BY id
                LIMIT ?
                """,
                (resultSet, rowNumber) -> new TourApiDetailTarget(
                        resultSet.getLong("id"),
                        resultSet.getString("source_content_id"),
                        resultSet.getString("source_content_type_id"),
                        "EV".equals(resultSet.getString("classification_code_1"))),
                afterId,
                batchSize);
    }

    private boolean hasTargetAfter(long afterId) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS(
                    SELECT 1
                    FROM tourism_contents
                    WHERE source_provider = 'TOUR_API'
                      AND status = 'ACTIVE'
                      AND deleted_at IS NULL
                      AND classification_code_1 IN ('NA', 'HS', 'VE', 'EX', 'LS', 'EV')
                      AND id > ?
                )
                """,
                Boolean.class,
                afterId);
        return Boolean.TRUE.equals(exists);
    }

    private void waitBeforeNextRequest(Duration delay) {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TourAPI 상세 backfill 대기가 중단되었습니다.", exception);
        }
    }

    public record BackfillBatchResult(
            long afterId,
            long nextAfterId,
            int targets,
            int completed,
            List<String> failedSourceContentIds,
            boolean hasMore) {

        public BackfillBatchResult {
            failedSourceContentIds = List.copyOf(failedSourceContentIds);
        }
    }
}
