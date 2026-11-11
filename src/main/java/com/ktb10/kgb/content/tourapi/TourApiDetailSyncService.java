package com.ktb10.kgb.content.tourapi;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 목록에서 신규·변경된 콘텐츠를 찾아 상세 원본을 수집하고 정규화합니다. */
@Service
public class TourApiDetailSyncService {

    private static final Logger log = LoggerFactory.getLogger(TourApiDetailSyncService.class);
    private final TourApiClient client;
    private final TourApiSnapshotStorage storage;
    private final TourApiDetailImportService importer;
    private final TourApiSchedulerProperties properties;

    public TourApiDetailSyncService(
            TourApiClient client,
            TourApiSnapshotStorage storage,
            TourApiDetailImportService importer,
            TourApiSchedulerProperties properties) {
        this.client = client;
        this.storage = storage;
        this.importer = importer;
        this.properties = properties;
    }

    public DetailSyncSummary synchronize(
            UUID runId,
            LocalDate syncDate,
            List<ContentChangeSet> baseChangeSets) {
        List<TourApiDetailTarget> targets = resolveTargets(baseChangeSets);
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
                        syncDate,
                        exception);
            }
        }
        log.info(
                "TourAPI detail sync completed: runId={}, targetDate={}, targets={}, completed={}, failed={}",
                runId,
                syncDate,
                targets.size(),
                completed,
                failed);
        return new DetailSyncSummary(targets.size(), completed, failed, changeSets);
    }

    private List<TourApiDetailTarget> resolveTargets(List<ContentChangeSet> baseChangeSets) {
        return baseChangeSets.stream()
                .filter(change -> change.lifecycleChange() != ContentLifecycleChange.INACTIVATED)
                .map(ContentChangeSet::detailTarget)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    ContentChangeSet synchronizeTarget(String runId, TourApiDetailTarget target) {
        return synchronizeTarget(
                runId,
                target,
                (common, intro) -> importer.importDetail(target, common, intro));
    }

    private ContentChangeSet synchronizeTarget(UUID runId, TourApiDetailTarget target) {
        return synchronizeTarget(
                runId.toString(),
                target,
                (common, intro) -> importer.importDetail(runId, target, common, intro));
    }

    private ContentChangeSet synchronizeTarget(
            String snapshotRunId,
            TourApiDetailTarget target,
            DetailImporter detailImporter) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                return synchronizeOne(snapshotRunId, target, detailImporter);
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

    private ContentChangeSet synchronizeOne(
            String runId,
            TourApiDetailTarget target,
            DetailImporter detailImporter) {
        byte[] common = client.fetchDetailCommon(target.sourceContentId());
        storage.saveDetail(runId, target.sourceContentId(), "detailCommon2", common);
        byte[] intro = null;
        if (target.event()) {
            intro = client.fetchDetailIntro(
                    target.sourceContentId(), target.sourceContentTypeId());
            storage.saveDetail(runId, target.sourceContentId(), "detailIntro2", intro);
        }
        return detailImporter.importDetail(common, intro);
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(properties.retryBackoff().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TourAPI 상세 동기화 재시도 대기가 중단되었습니다.", exception);
        }
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

    @FunctionalInterface
    private interface DetailImporter {

        ContentChangeSet importDetail(byte[] common, byte[] intro);
    }
}
