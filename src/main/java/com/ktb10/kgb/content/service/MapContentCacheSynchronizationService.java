package com.ktb10.kgb.content.service;

import com.ktb10.kgb.content.tourapi.TourApiCacheRefreshRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** DB에 영속화된 TourAPI 갱신 버전을 각 인스턴스의 Caffeine 캐시에 반영합니다. */
@Service
public class MapContentCacheSynchronizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            MapContentCacheSynchronizationService.class);

    private final MapContentCommonCache commonCache;
    private final TourApiCacheRefreshRepository refreshRepository;
    private final AtomicLong lastAppliedBatchId = new AtomicLong();

    public MapContentCacheSynchronizationService(
            MapContentCommonCache commonCache,
            TourApiCacheRefreshRepository refreshRepository) {
        this.commonCache = commonCache;
        this.refreshRepository = refreshRepository;
    }

    /** Preload 시작 전 버전을 기준으로 잡아 동시 배치 완료도 다음 폴링에서 반영합니다. */
    public void preload() {
        long baseline = refreshRepository.findLatestBatchId();
        commonCache.refresh();
        lastAppliedBatchId.set(baseline);
    }

    @Scheduled(
            fixedDelayString = "${map.common-cache-sync-poll-interval-millis:30000}",
            initialDelayString = "${map.common-cache-sync-poll-initial-delay-millis:30000}")
    public synchronized void applyAvailableRefreshes() {
        long after = lastAppliedBatchId.get();
        long through = refreshRepository.findLatestBatchId();
        if (through <= after) {
            return;
        }
        List<MapContentCacheTile> tiles =
                refreshRepository.findTilesAfter(after, through);
        commonCache.refreshByTiles(tiles);
        lastAppliedBatchId.set(through);
        LOGGER.info(
                "Map content cache synchronized: previousBatchId={}, appliedBatchId={}, tiles={}",
                after,
                through,
                tiles.size());
    }
}
