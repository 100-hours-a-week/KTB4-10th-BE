package com.ktb10.kgb.content.tourapi;

import com.ktb10.kgb.content.event.InterestedContentChangesDetectedEvent;
import com.ktb10.kgb.content.service.MapContentCacheSynchronizationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** 기준일별 잠금을 획득하고 TourAPI 동기화의 재시도와 실행 이력을 관리합니다. */
@Service
public class TourApiSyncRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(TourApiSyncRunner.class);
    private final TourApiSyncService syncService;
    private final TourApiSyncRunRepository runRepository;
    private final MapContentCacheSynchronizationService cacheSynchronizationService;
    private final InterestedContentChangeCandidateSelector notificationCandidateSelector;
    private final ApplicationEventPublisher eventPublisher;
    private final TourApiSchedulerProperties properties;
    private final Clock clock;

    public TourApiSyncRunner(
            TourApiSyncService syncService,
            TourApiSyncRunRepository runRepository,
            MapContentCacheSynchronizationService cacheSynchronizationService,
            InterestedContentChangeCandidateSelector notificationCandidateSelector,
            ApplicationEventPublisher eventPublisher,
            TourApiSchedulerProperties properties,
            Clock clock) {
        this.syncService = syncService;
        this.runRepository = runRepository;
        this.cacheSynchronizationService = cacheSynchronizationService;
        this.notificationCandidateSelector = notificationCandidateSelector;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean runIfDue(LocalDate syncDate) {
        UUID runId = UUID.randomUUID();
        Instant startedAt = clock.instant();
        boolean acquired = runRepository.tryStart(
                runId,
                syncDate,
                startedAt,
                startedAt.minus(properties.staleRunTimeout()));
        if (!acquired) {
            LOGGER.info("TourAPI sync skipped: syncDate={}, reason=duplicate", syncDate);
            return false;
        }

        ImportSummary summary;
        try {
            summary = synchronizeWithRetry(runId, syncDate);
        } catch (RuntimeException failure) {
            recordFailure(runId, syncDate, "SYNCHRONIZE", failure);
            return false;
        }
        try {
            runRepository.complete(runId, summary, clock.instant());
        } catch (RuntimeException failure) {
            recordFailure(runId, syncDate, "COMPLETE", failure);
            return false;
        }
        try {
            cacheSynchronizationService.applyAvailableRefreshes();
        } catch (RuntimeException failure) {
            LOGGER.error(
                    "TourAPI 동기화 완료 후 로컬 지도 캐시 반영에 실패했습니다. "
                            + "runId={}, syncDate={}",
                    runId,
                    syncDate,
                    failure);
        }
        LOGGER.info(
                "TourAPI sync completed: runId={}, syncDate={}, sourceContents={}, "
                        + "importedContents={}, inactiveContents={}, "
                        + "sourceEvents={}, importedEvents={}",
                runId,
                syncDate,
                summary.sourceContents(),
                summary.importedContents(),
                summary.inactiveContents(),
                summary.sourceEvents(),
                summary.importedEvents());
        publishContentChangeNotification(syncDate, summary);
        return true;
    }

    private void publishContentChangeNotification(
            LocalDate syncDate,
            ImportSummary summary) {
        try {
            List<InterestedContentChangeCandidate> candidates =
                    notificationCandidateSelector.select(summary.changeSets());
            if (!candidates.isEmpty()) {
                eventPublisher.publishEvent(new InterestedContentChangesDetectedEvent(
                        syncDate,
                        candidates));
            }
        } catch (RuntimeException failure) {
            LOGGER.error(
                    "TourAPI 동기화 성공 후 변경 알림 작업 등록에 실패했습니다. syncDate={}",
                    syncDate,
                    failure);
        }
    }

    private void recordFailure(
            UUID runId,
            LocalDate syncDate,
            String failureStage,
            RuntimeException failure) {
        runRepository.fail(runId, failureStage, failure, clock.instant());
        LOGGER.error(
                "TourAPI sync failed: runId={}, syncDate={}, stage={}, reason={}",
                runId,
                syncDate,
                failureStage,
                failure.getMessage(),
                failure);
    }

    private ImportSummary synchronizeWithRetry(UUID runId, LocalDate syncDate) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            runRepository.recordAttempt(runId, attempt, clock.instant());
            try {
                return syncService.synchronize(runId, syncDate);
            } catch (TourApiException failure) {
                lastFailure = failure;
                if (!failure.isRetryable() || attempt == properties.maxAttempts()) {
                    throw failure;
                }
                waitBeforeRetry();
            }
        }
        throw lastFailure == null
                ? new IllegalStateException("TourAPI 동기화 재시도 상태가 올바르지 않습니다.")
                : lastFailure;
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(properties.retryBackoff().toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TourAPI 동기화 재시도 대기가 중단되었습니다.", interrupted);
        }
    }
}
