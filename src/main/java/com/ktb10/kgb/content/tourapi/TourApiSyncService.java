package com.ktb10.kgb.content.tourapi;

import com.ktb10.kgb.content.tourapi.TourApiSnapshotValidator.SnapshotType;
import com.ktb10.kgb.content.tourapi.TourApiSnapshotValidator.ValidatedSnapshot;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** TourAPI 데이터를 가져와 검증·보관한 뒤 DB에 적재합니다. */
@Service
public class TourApiSyncService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final TourApiClient client;
    private final TourApiSnapshotValidator validator;
    private final TourApiSnapshotStorage storage;
    private final TourApiImportService importer;
    private final TourApiProperties properties;
    private final Clock clock;

    public TourApiSyncService(
            TourApiClient client,
            TourApiSnapshotValidator validator,
            TourApiSnapshotStorage storage,
            TourApiImportService importer,
            TourApiProperties properties,
            Clock clock) {
        this.client = client;
        this.validator = validator;
        this.storage = storage;
        this.importer = importer;
        this.properties = properties;
        this.clock = clock;
    }

    public ImportSummary synchronize() {
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
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
        return importer.importSnapshots(area.raw(), festival.raw());
    }
}
