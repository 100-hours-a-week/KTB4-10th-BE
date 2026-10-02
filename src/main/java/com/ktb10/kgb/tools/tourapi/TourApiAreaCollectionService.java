package com.ktb10.kgb.tools.tourapi;

import com.ktb10.kgb.tools.tourapi.TourApiAreaSnapshotValidator.ValidatedAreaSnapshot;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 전국 목록을 수집·검증·저장한 뒤 기존 증분 적재 흐름에 전달합니다. */
@Service
public class TourApiAreaCollectionService {

    private static final Logger log =
            LoggerFactory.getLogger(TourApiAreaCollectionService.class);

    private final TourApiAreaClient client;
    private final TourApiAreaSnapshotValidator validator;
    private final TourApiSnapshotStorage storage;
    private final TourApiInitialImportService importer;
    private final TourApiCollectionProperties properties;

    public TourApiAreaCollectionService(
            TourApiAreaClient client,
            TourApiAreaSnapshotValidator validator,
            TourApiSnapshotStorage storage,
            TourApiInitialImportService importer,
            TourApiCollectionProperties properties) {
        this.client = client;
        this.validator = validator;
        this.storage = storage;
        this.importer = importer;
        this.properties = properties;
    }

    public TourApiAreaCollectionResult collectAndImport() {
        long startedAt = System.nanoTime();
        FetchResult fetchResult = fetchValidatedSnapshot();
        ValidatedAreaSnapshot snapshot = fetchResult.snapshot();
        String storedAt = storage.save(snapshot.raw());
        ImportSummary summary = importer.importAreaSnapshot(snapshot.raw());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        log.info(
                "TourAPI 전국 목록 수집 완료: totalCount={}, bytes={}, imported={}, "
                        + "attempts={}, elapsedMs={}, storedAt={}",
                snapshot.totalCount(),
                snapshot.raw().length,
                summary.importedContents(),
                fetchResult.attempts(),
                elapsed.toMillis(),
                storedAt);
        return new TourApiAreaCollectionResult(
                snapshot.totalCount(),
                snapshot.raw().length,
                summary,
                fetchResult.attempts(),
                elapsed,
                storedAt);
    }

    private FetchResult fetchValidatedSnapshot() {
        TourApiCollectionException lastFailure = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                byte[] raw = client.fetchSnapshot();
                return new FetchResult(validator.validate(raw), attempt);
            } catch (TourApiCollectionException exception) {
                lastFailure = exception;
                log.warn(
                        "TourAPI 전국 목록 수집 실패: attempt={}, retryable={}, reason={}",
                        attempt,
                        exception.isRetryable(),
                        exception.getMessage());
                if (!exception.isRetryable()) {
                    throw exception;
                }
            }
        }
        throw new TourApiCollectionException(
                "TourAPI 전국 목록 수집 재시도 횟수를 초과했습니다.",
                false,
                lastFailure);
    }

    private record FetchResult(
            ValidatedAreaSnapshot snapshot,
            int attempts) {
    }
}
