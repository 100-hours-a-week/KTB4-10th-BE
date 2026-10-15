package com.ktb10.kgb.content.tourapi;

import com.ktb10.kgb.content.tourapi.TourApiSnapshotValidator.SnapshotType;
import com.ktb10.kgb.content.tourapi.TourApiSnapshotValidator.ValidatedSnapshot;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** TourAPI 데이터를 가져와 검증·보관한 뒤 DB에 적재합니다. */
@Service
public class TourApiSyncService {

    private final TourApiClient client;
    private final TourApiSnapshotValidator validator;
    private final TourApiSnapshotStorage storage;
    private final TourApiImportService importer;
    private final TourApiDetailSyncService detailSyncService;
    private final TourApiProperties properties;

    public TourApiSyncService(
            TourApiClient client,
            TourApiSnapshotValidator validator,
            TourApiSnapshotStorage storage,
            TourApiImportService importer,
            TourApiDetailSyncService detailSyncService,
            TourApiProperties properties) {
        this.client = client;
        this.validator = validator;
        this.storage = storage;
        this.importer = importer;
        this.detailSyncService = detailSyncService;
        this.properties = properties;
    }

    public ImportSummary synchronize(LocalDate syncDate) {
        LocalDate today = syncDate;
        LocalDate festivalStartDate = today.minusDays(properties.festivalLookbackDays());
        LocalDate festivalEndDate = today.plusYears(properties.festivalLookaheadYears());
        ValidatedSnapshot area = validator.validate(
                SnapshotType.AREA,
                client.fetchAreaSnapshot());
        ValidatedSnapshot festival = validator.validate(
                SnapshotType.FESTIVAL,
                client.fetchFestivalSnapshot(festivalStartDate, festivalEndDate));
        String runId = UUID.randomUUID().toString();
        storage.save(runId, "areaBasedList2.json", area.raw());
        storage.save(runId, "searchFestival2.json", festival.raw());
        ImportSummary summary = importer.importSnapshots(area.raw(), festival.raw());
        detailSyncService.synchronize(runId, syncDate.minusDays(1), area.raw());
        return summary;
    }
}
