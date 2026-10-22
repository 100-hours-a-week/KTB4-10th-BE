package com.ktb10.kgb.content.tourapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 목록에서 신규·변경된 콘텐츠를 찾아 상세 원본을 수집하고 정규화합니다. */
@Service
public class TourApiDetailSyncService {

    private static final Logger log = LoggerFactory.getLogger(TourApiDetailSyncService.class);
    private static final DateTimeFormatter SOURCE_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> ALLOWED_CLASSIFICATIONS =
            Set.of("NA", "HS", "VE", "EX", "LS", "EV");

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TourApiClient client;
    private final TourApiSnapshotStorage storage;
    private final TourApiDetailImportService importer;
    private final TourApiSchedulerProperties properties;

    public TourApiDetailSyncService(
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            TourApiClient client,
            TourApiSnapshotStorage storage,
            TourApiDetailImportService importer,
            TourApiSchedulerProperties properties) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.client = client;
        this.storage = storage;
        this.importer = importer;
        this.properties = properties;
    }

    public DetailSyncSummary synchronize(
            String runId,
            LocalDate targetDate,
            byte[] areaRaw,
            Set<String> newSourceContentIds) {
        List<TourApiDetailTarget> targets = resolveTargets(
                areaRaw, targetDate, newSourceContentIds);
        int completed = 0;
        int failed = 0;
        List<ContentChangeSet> changeSets = new ArrayList<>();
        for (TourApiDetailTarget target : targets) {
            try {
                ContentChangeSet changeSet = synchronizeTarget(runId, target);
                if (!changeSet.changedFields().isEmpty()) {
                    changeSets.add(changeSet);
                }
                completed++;
            } catch (RuntimeException exception) {
                failed++;
                log.error(
                        "TourAPI detail sync failed: runId={}, contentId={}, targetDate={}",
                        runId,
                        target.sourceContentId(),
                        targetDate,
                        exception);
            }
        }
        log.info(
                "TourAPI detail sync completed: runId={}, targetDate={}, targets={}, completed={}, failed={}",
                runId,
                targetDate,
                targets.size(),
                completed,
                failed);
        return new DetailSyncSummary(targets.size(), completed, failed, changeSets);
    }

    private List<TourApiDetailTarget> resolveTargets(
            byte[] areaRaw,
            LocalDate targetDate,
            Set<String> newSourceContentIds) {
        Map<String, SourceTarget> sourceTargets = new LinkedHashMap<>();
        for (JsonNode item : readItems(areaRaw)) {
            String classification = text(item, "lclsSystm1");
            if (!ALLOWED_CLASSIFICATIONS.contains(classification)) {
                continue;
            }
            String sourceContentId = text(item, "contentid");
            if (sourceContentId == null) {
                continue;
            }
            boolean newlyImported = newSourceContentIds.contains(sourceContentId);
            boolean createdOrModifiedOnTargetDate =
                    targetDate.equals(sourceDate(item, "createdtime"))
                            || targetDate.equals(sourceDate(item, "modifiedtime"));
            if (!newlyImported && !createdOrModifiedOnTargetDate) {
                continue;
            }
            String sourceContentTypeId = text(item, "contenttypeid");
            sourceTargets.put(
                    sourceContentId,
                    new SourceTarget(
                            sourceContentId,
                            sourceContentTypeId,
                            "EV".equals(classification)));
        }
        List<TourApiDetailTarget> result = new ArrayList<>();
        for (SourceTarget source : sourceTargets.values()) {
            jdbcTemplate.query(
                    """
                    SELECT id
                    FROM tourism_contents
                    WHERE source_provider = 'TOUR_API' AND source_content_id = ?
                      AND status = 'ACTIVE' AND deleted_at IS NULL
                    """,
                    (resultSet, rowNumber) -> resultSet.getLong("id"),
                    source.sourceContentId()).stream().findFirst().ifPresent(id -> result.add(
                            new TourApiDetailTarget(
                                    id,
                                    source.sourceContentId(),
                                    source.sourceContentTypeId(),
                                    source.event())));
        }
        return result;
    }

    ContentChangeSet synchronizeTarget(String runId, TourApiDetailTarget target) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                return synchronizeOne(runId, target);
            } catch (TourApiException exception) {
                lastFailure = exception;
                if (!exception.isRetryable() || attempt == properties.maxAttempts()) {
                    throw exception;
                }
                waitBeforeRetry();
            }
        }
        throw lastFailure == null
                ? new IllegalStateException("TourAPI 상세 동기화 재시도 상태가 올바르지 않습니다.")
                : lastFailure;
    }

    private ContentChangeSet synchronizeOne(String runId, TourApiDetailTarget target) {
        byte[] common = client.fetchDetailCommon(target.sourceContentId());
        storage.saveDetail(runId, target.sourceContentId(), "detailCommon2", common);
        byte[] intro = null;
        if (target.event()) {
            intro = client.fetchDetailIntro(
                    target.sourceContentId(), target.sourceContentTypeId());
            storage.saveDetail(runId, target.sourceContentId(), "detailIntro2", intro);
        }
        return importer.importDetail(target, common, intro);
    }

    private List<JsonNode> readItems(byte[] raw) {
        try {
            JsonNode items = objectMapper.readTree(raw)
                    .path("response").path("body").path("items").path("item");
            if (!items.isArray()) {
                throw new IllegalArgumentException("areaBasedList2 응답의 item이 배열이 아닙니다.");
            }
            List<JsonNode> result = new ArrayList<>();
            items.forEach(result::add);
            return result;
        } catch (IOException exception) {
            throw new UncheckedIOException("areaBasedList2 JSON을 읽을 수 없습니다.", exception);
        }
    }

    private static LocalDate sourceDate(JsonNode item, String field) {
        String value = text(item, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, SOURCE_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(properties.retryBackoff().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TourAPI 상세 동기화 재시도 대기가 중단되었습니다.", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record DetailSyncSummary(
            int targets,
            int completed,
            int failed,
            List<ContentChangeSet> changeSets) {

        public DetailSyncSummary {
            changeSets = List.copyOf(changeSets);
        }
    }

    private record SourceTarget(
            String sourceContentId,
            String sourceContentTypeId,
            boolean event) {
    }
}
