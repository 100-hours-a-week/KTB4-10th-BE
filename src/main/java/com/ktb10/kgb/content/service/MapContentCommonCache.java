package com.ktb10.kgb.content.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 상세 지도를 고정 타일로 나누어 회원과 무관한 조회 결과를 재사용합니다. */
@Component
public class MapContentCommonCache {

    private static final long MAXIMUM_SIZE = 5_000L;
    private static final double TILE_SIZE = 0.05;
    private static final long MAX_TILES_PER_REQUEST = 256L;

    private final MapContentCommonLoader loader;
    private final MapPerformanceMetrics performanceMetrics;
    private final Cache<TileKey, List<MapContentCommonData>> cache;
    private final boolean enabled;

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
                .description("상세 지도 공통 타일 캐시 적중률")
                .register(meterRegistry);
        Gauge.builder("kgb.map.cache.eviction.count", cache,
                        value -> value.stats().evictionCount())
                .description("상세 지도 공통 타일 캐시 퇴거 횟수")
                .register(meterRegistry);
        Gauge.builder("kgb.map.cache.size", cache, Cache::estimatedSize)
                .description("상세 지도 공통 타일 캐시 항목 수")
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
        long southCell = cellOf(south);
        long westCell = cellOf(west);
        long northCell = cellOf(north);
        long eastCell = cellOf(east);
        long tileCount = (northCell - southCell + 1) * (eastCell - westCell + 1);

        if (tileCount > MAX_TILES_PER_REQUEST) {
            return performanceMetrics.recordCommonQuery(
                    () -> loader.load(south, west, north, east));
        }

        Map<Long, MapContentCommonData> mergedContents = new LinkedHashMap<>();
        for (long latitudeCell = southCell; latitudeCell <= northCell; latitudeCell++) {
            for (long longitudeCell = westCell; longitudeCell <= eastCell; longitudeCell++) {
                TileKey key = new TileKey(latitudeCell, longitudeCell);
                List<MapContentCommonData> tileContents = cache.get(
                        key,
                        ignored -> performanceMetrics.recordCommonQuery(
                                () -> loader.load(
                                        key.south(),
                                        key.west(),
                                        key.north(),
                                        key.east())));
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
    }

    /** 애플리케이션 시작 시 전체 상세 지도 캐시를 현재 DB 데이터로 구성합니다. */
    public void refresh() {
        if (!enabled) {
            return;
        }

        Map<TileKey, List<MapContentCommonData>> refreshed = new HashMap<>();
        loader.loadAllCacheable().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        content -> new TileKey(
                                cellOf(content.latitude()),
                                cellOf(content.longitude()))))
                .forEach((key, contents) -> refreshed.put(
                        key,
                        contents.stream()
                                .sorted(Comparator.comparing(MapContentCommonData::id))
                                .toList()));

        cache.invalidateAll();
        cache.putAll(refreshed);
        cache.cleanUp();
    }

    /**
     * 관광 데이터 배치에서 변경된 위치가 속한 타일만 최신 DB 데이터로 교체합니다.
     * 좌표가 이동하거나 콘텐츠가 삭제된 경우 이전 위치와 현재 위치를 모두 전달해야 합니다.
     */
    public void refreshByLocations(Collection<MapContentCacheLocation> locations) {
        if (!enabled || locations.isEmpty()) {
            return;
        }

        Set<TileKey> affectedKeys = locations.stream()
                .map(location -> new TileKey(
                        cellOf(location.latitude()),
                        cellOf(location.longitude())))
                .collect(java.util.stream.Collectors.toSet());

        affectedKeys.forEach(key -> cache.put(
                key,
                performanceMetrics.recordCommonQuery(
                        () -> loader.load(
                                key.south(),
                                key.west(),
                                key.north(),
                                key.east()))));
        cache.cleanUp();
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
