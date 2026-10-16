package com.ktb10.kgb.content.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.common.observability.MapPerformanceMetrics;
import com.ktb10.kgb.content.repository.MapContentCommonData;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 지도를 고정 타일로 나누어 회원과 무관한 원본 조회 결과를 재사용합니다. */
@Component
public class MapContentCommonCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapContentCommonCache.class);
    private static final long MAXIMUM_SIZE = 5_000L;
    private static final double TILE_SIZE = 0.5;

    private final MapContentCommonLoader loader;
    private final MapPerformanceMetrics performanceMetrics;
    private final Cache<TileKey, List<MapContentCommonData>> cache;
    private final boolean enabled;
    private final ReentrantReadWriteLock snapshotLock = new ReentrantReadWriteLock();
    private volatile boolean snapshotReady;

    public MapContentCommonCache(
            MapContentCommonLoader loader,
            MapPerformanceMetrics performanceMetrics,
            MeterRegistry meterRegistry,
            @Value("${map.common-cache-enabled:true}") boolean enabled) {
        this.loader = loader;
        this.performanceMetrics = performanceMetrics;
        this.enabled = enabled;
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_SIZE)
                .recordStats()
                .build();
        Gauge.builder("kgb.map.cache.hit.ratio", cache,
                        value -> value.stats().hitRate())
                .description("지도 공통 타일 캐시 적중률")
                .register(meterRegistry);
        Gauge.builder("kgb.map.cache.eviction.count", cache,
                        value -> value.stats().evictionCount())
                .description("지도 공통 타일 캐시 퇴거 횟수")
                .register(meterRegistry);
        Gauge.builder("kgb.map.cache.size", cache, Cache::estimatedSize)
                .description("지도 공통 타일 캐시 항목 수")
                .register(meterRegistry);
    }

    public List<MapContentCommonData> get(
            double south,
            double west,
            double north,
            double east) {
        if (!enabled) {
            return performanceMetrics.recordCommonQuery(
                    () -> loader.load(south, west, north, east));
        }
        snapshotLock.readLock().lock();
        try {
            if (!snapshotReady) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
            }
            long southCell = cellOf(south);
            long westCell = cellOf(west);
            long northCell = cellOf(north);
            long eastCell = cellOf(east);
            Map<Long, MapContentCommonData> mergedContents = new LinkedHashMap<>();
            for (long latitudeCell = southCell; latitudeCell <= northCell; latitudeCell++) {
                for (long longitudeCell = westCell; longitudeCell <= eastCell; longitudeCell++) {
                    TileKey key = new TileKey(latitudeCell, longitudeCell);
                    List<MapContentCommonData> tileContents = cache.getIfPresent(key);
                    if (tileContents == null) {
                        tileContents = List.of();
                    }
                    tileContents.forEach(content -> mergedContents.putIfAbsent(content.id(), content));
                }
            }

            return mergedContents.values().stream()
                    .filter(content -> content.latitude() >= south)
                    .filter(content -> content.latitude() <= north)
                    .filter(content -> content.longitude() >= west)
                    .filter(content -> content.longitude() <= east)
                    .sorted(Comparator.comparing(MapContentCommonData::id))
                    .toList();
        } finally {
            snapshotLock.readLock().unlock();
        }
    }

    /** 애플리케이션 시작 시 전체 상세 지도 캐시를 현재 DB 데이터로 구성합니다. */
    public void refresh() {
        if (!enabled) {
            return;
        }

        List<MapContentCommonData> allContents = loader.loadAllCacheable();
        Map<TileKey, List<MapContentCommonData>> refreshed = new HashMap<>();
        allContents.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        content -> new TileKey(
                                cellOf(content.latitude()),
                                cellOf(content.longitude()))))
                .forEach((key, contents) -> refreshed.put(
                        key,
                        contents.stream()
                                .sorted(Comparator.comparing(MapContentCommonData::id))
                                .toList()));

        snapshotLock.writeLock().lock();
        try {
            cache.invalidateAll();
            cache.putAll(refreshed);
            cache.cleanUp();
            snapshotReady = true;
        } finally {
            snapshotLock.writeLock().unlock();
        }
        LOGGER.info(
                "Map content cache full refresh completed: tiles={}, contents={}",
                refreshed.size(),
                allContents.size());
    }

    /**
     * 관광 데이터 배치에서 변경된 위치가 속한 타일만 최신 DB 데이터로 교체합니다.
     * 좌표가 이동하거나 콘텐츠가 삭제된 경우 이전 위치와 현재 위치를 모두 전달해야 합니다.
     */
    public void refreshByLocations(Collection<MapContentCacheLocation> locations) {
        if (!enabled) {
            return;
        }

        Set<TileKey> affectedKeys = locations.stream()
                .map(location -> new TileKey(
                        cellOf(location.latitude()),
                        cellOf(location.longitude())))
                .collect(java.util.stream.Collectors.toSet());

        if (!snapshotReady) {
            LOGGER.info(
                    "Map content cache partial refresh fallback: requestedLocations={}, "
                            + "affectedTiles={}, fallback=full",
                    locations.size(),
                    affectedKeys.size());
            refresh();
            return;
        }

        if (locations.isEmpty()) {
            return;
        }

        Map<TileKey, List<MapContentCommonData>> replacements = new HashMap<>();
        affectedKeys.forEach(key -> replacements.put(
                key,
                performanceMetrics.recordCommonQuery(
                        () -> loader.load(
                                key.south(),
                                key.west(),
                                key.north(),
                                key.east()))));
        snapshotLock.writeLock().lock();
        try {
            cache.putAll(replacements);
            cache.cleanUp();
        } finally {
            snapshotLock.writeLock().unlock();
        }
        LOGGER.info(
                "Map content cache partial refresh completed: requestedLocations={}, "
                        + "affectedTiles={}, fallback=false",
                locations.size(),
                affectedKeys.size());
    }

    private static long cellOf(double coordinate) {
        return (long) Math.floor(coordinate / TILE_SIZE);
    }

    private record TileKey(long latitudeCell, long longitudeCell) {

        private double south() {
            return latitudeCell * TILE_SIZE;
        }

        private double west() {
            return longitudeCell * TILE_SIZE;
        }

        private double north() {
            return south() + TILE_SIZE;
        }

        private double east() {
            return west() + TILE_SIZE;
        }
    }
}
