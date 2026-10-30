package com.ktb10.kgb.content.tourapi;

import com.ktb10.kgb.content.service.MapContentCacheLocation;
import com.ktb10.kgb.content.service.MapContentCacheRefreshService;
import com.ktb10.kgb.content.service.MapContentCacheTile;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관광 콘텐츠 기준정보와 캐시 갱신 계획을 하나의 트랜잭션으로 저장합니다. */
@Service
public class TourApiBaseImportTransactionService {

    private static final Set<ContentChangedField> MAP_CACHE_FIELDS = EnumSet.of(
            ContentChangedField.TITLE,
            ContentChangedField.THUMBNAIL_URL,
            ContentChangedField.ADDRESS,
            ContentChangedField.START_DATE,
            ContentChangedField.END_DATE,
            ContentChangedField.LOCATION,
            ContentChangedField.CLASSIFICATION_CODE_1,
            ContentChangedField.STATUS);

    private final TourApiImportService importer;
    private final MapContentCacheRefreshService cacheRefreshService;
    private final TourApiCacheRefreshRepository cacheRefreshRepository;
    private final TourApiContentChangeOccurrenceRepository changeOccurrenceRepository;

    public TourApiBaseImportTransactionService(
            TourApiImportService importer,
            MapContentCacheRefreshService cacheRefreshService,
            TourApiCacheRefreshRepository cacheRefreshRepository,
            TourApiContentChangeOccurrenceRepository changeOccurrenceRepository) {
        this.importer = importer;
        this.cacheRefreshService = cacheRefreshService;
        this.cacheRefreshRepository = cacheRefreshRepository;
        this.changeOccurrenceRepository = changeOccurrenceRepository;
    }

    @Transactional
    public ImportSummary importSnapshots(
            UUID runId,
            LocalDate syncDate,
            byte[] areaSnapshot,
            byte[] festivalSnapshot,
            Instant createdAt) {
        ImportSummary summary = importer.importSnapshots(
                areaSnapshot,
                festivalSnapshot,
                syncDate);
        List<MapContentCacheTile> changedTiles = cacheRefreshService.resolveTiles(
                cacheLocations(summary.changeSets()), syncDate);
        cacheRefreshRepository.save(runId, changedTiles, createdAt);
        changeOccurrenceRepository.merge(runId, summary.changeSets());
        return summary;
    }

    private List<MapContentCacheLocation> cacheLocations(List<ContentChangeSet> changeSets) {
        return changeSets.stream()
                .filter(change -> change.changedFields().stream().anyMatch(MAP_CACHE_FIELDS::contains))
                .flatMap(change -> java.util.stream.Stream.of(
                        change.previousLocation(),
                        change.currentLocation()))
                .filter(java.util.Objects::nonNull)
                .map(location -> new MapContentCacheLocation(
                        location.latitude(),
                        location.longitude()))
                .distinct()
                .toList();
    }
}
