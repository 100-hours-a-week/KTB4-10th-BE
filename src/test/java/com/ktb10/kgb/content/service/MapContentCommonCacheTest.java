package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.common.observability.MapPerformanceMetrics;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.repository.MapContentCommonData;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapContentCommonCacheTest {

    @Mock
    private MapContentCommonLoader loader;

    private MapContentCommonCache commonCache;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        commonCache = new MapContentCommonCache(
                loader,
                new MapPerformanceMetrics(meterRegistry),
                meterRegistry,
                true);
    }

    @Test
    void reusesCommonResultForRepeatedRequestsWithinSameTile() {
        when(loader.load(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(commonContent(1L, 37.405, 127.105)));

        List<MapContentCommonData> first = commonCache.get(
                37.401, 127.101, 37.409, 127.109);
        List<MapContentCommonData> second = commonCache.get(
                37.402, 127.102, 37.408, 127.108);

        assertThat(first).extracting(MapContentCommonData::id).containsExactly(1L);
        assertThat(second).extracting(MapContentCommonData::id).containsExactly(1L);
        verify(loader, times(1)).load(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertThat(meterRegistry.get("kgb.map.cache.hit.ratio").gauge().value())
                .isEqualTo(0.5);
        assertThat(meterRegistry.get("kgb.map.cache.size").gauge().value())
                .isEqualTo(1.0);
    }

    @Test
    void mergesTilesWithoutDuplicatesAndFiltersActualBounds() {
        when(loader.load(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(
                        commonContent(2L, 37.405, 127.102),
                        commonContent(1L, 37.405, 127.098),
                        commonContent(3L, 37.405, 127.109)));

        List<MapContentCommonData> result = commonCache.get(
                37.401, 127.095, 37.409, 127.105);

        assertThat(result).extracting(MapContentCommonData::id).containsExactly(1L, 2L);
        verify(loader, times(2)).load(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void reusesSameTileRegardlessOfDetailedZoomLevel() {
        when(loader.load(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(commonContent(1L, 37.405, 127.105)));

        commonCache.get(37.401, 127.101, 37.409, 127.109);
        commonCache.get(37.401, 127.101, 37.409, 127.109);

        verify(loader, times(1)).load(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void loadsWideBoundsDirectlyWithoutPopulatingTiles() {
        MapContentCommonData content = commonContent(1L, 37.5, 127.2);
        when(loader.load(37.4, 127.1, 38.2, 127.9)).thenReturn(List.of(content));

        List<MapContentCommonData> first = commonCache.get(
                37.4, 127.1, 38.2, 127.9);
        List<MapContentCommonData> second = commonCache.get(
                37.4, 127.1, 38.2, 127.9);

        assertThat(first).containsExactly(content);
        assertThat(second).containsExactly(content);
        verify(loader, times(2)).load(37.4, 127.1, 38.2, 127.9);
    }

    @Test
    void loadsEveryRequestDirectlyWhenCacheIsDisabled() {
        meterRegistry = new SimpleMeterRegistry();
        commonCache = new MapContentCommonCache(
                loader,
                new MapPerformanceMetrics(meterRegistry),
                meterRegistry,
                false);
        when(loader.load(37.401, 127.101, 37.409, 127.109))
                .thenReturn(List.of(commonContent(1L, 37.405, 127.105)));

        commonCache.get(37.401, 127.101, 37.409, 127.109);
        commonCache.get(37.401, 127.101, 37.409, 127.109);

        verify(loader, times(2)).load(37.401, 127.101, 37.409, 127.109);
        assertThat(meterRegistry.get("kgb.map.cache.size").gauge().value())
                .isZero();
    }

    @Test
    void refreshReplacesCacheWithAllCurrentContentsGroupedByTile() {
        MapContentCommonData first = commonContent(1L, 37.405, 127.105);
        MapContentCommonData second = commonContent(2L, 35.185, 129.075);
        when(loader.loadAllCacheable()).thenReturn(List.of(first, second));

        commonCache.refresh();

        assertThat(commonCache.get(37.401, 127.101, 37.409, 127.109))
                .containsExactly(first);
        assertThat(commonCache.get(35.181, 129.071, 35.189, 129.079))
                .containsExactly(second);
        verify(loader, times(1)).loadAllCacheable();
        verify(loader, times(0)).load(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertThat(meterRegistry.get("kgb.map.cache.size").gauge().value())
                .isEqualTo(2.0);
    }

    @Test
    void doesNotRefreshWhenCacheIsDisabled() {
        commonCache = new MapContentCommonCache(
                loader,
                new MapPerformanceMetrics(meterRegistry),
                meterRegistry,
                false);

        commonCache.refresh();

        verify(loader, times(0)).loadAllCacheable();
    }

    @Test
    void refreshesOnlyTilesContainingChangedLocations() {
        MapContentCommonData before = commonContent(1L, 37.405, 127.105);
        MapContentCommonData unchanged = commonContent(2L, 35.185, 129.075);
        when(loader.loadAllCacheable()).thenReturn(List.of(before, unchanged));
        commonCache.refresh();

        MapContentCommonData after = commonContent(1L, 37.455, 127.155);
        when(loader.load(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(invocation ->
                        ((double) invocation.getArgument(0)) < 37.45
                                ? List.of()
                                : List.of(after));

        commonCache.refreshByLocations(List.of(
                new MapContentCacheLocation(37.405, 127.105),
                new MapContentCacheLocation(37.455, 127.155)));

        assertThat(commonCache.get(37.401, 127.101, 37.409, 127.109)).isEmpty();
        assertThat(commonCache.get(37.451, 127.151, 37.459, 127.159))
                .containsExactly(after);
        assertThat(commonCache.get(35.181, 129.071, 35.189, 129.079))
                .containsExactly(unchanged);
        verify(loader, times(2)).load(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void refreshesChangedTileOnce() {
        MapContentCommonData before = commonContent(1L, 37.405, 127.105);
        when(loader.load(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(before));
        commonCache.get(37.401, 127.101, 37.409, 127.109);

        MapContentCommonData after = commonContent(1L, 37.406, 127.106);
        when(loader.load(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(after));

        commonCache.refreshByLocations(List.of(
                new MapContentCacheLocation(37.405, 127.105)));

        assertThat(commonCache.get(37.401, 127.101, 37.409, 127.109))
                .containsExactly(after);
        verify(loader, times(2)).load(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    private MapContentCommonData commonContent(
            Long id,
            double latitude,
            double longitude) {
        return new MapContentCommonData(
                id,
                String.valueOf(id),
                "장소 " + id,
                ContentType.PLACE,
                "주소",
                latitude,
                longitude,
                null,
                null);
    }
}
