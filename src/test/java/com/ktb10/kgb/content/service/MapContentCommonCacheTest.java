package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.common.observability.MapPerformanceMetrics;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.repository.MapContentCommonData;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
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
    void reusesSnapshotForRepeatedRequestsWithoutDatabaseFallback() {
        when(loader.loadAllCacheable())
                .thenReturn(List.of(commonContent(1L, 37.405, 127.105)));
        commonCache.refresh();

        List<MapContentCommonData> first = commonCache.get(
                37.401, 127.101, 37.409, 127.109);
        List<MapContentCommonData> second = commonCache.get(
                37.402, 127.102, 37.408, 127.108);

        assertThat(first).extracting(MapContentCommonData::id).containsExactly(1L);
        assertThat(second).extracting(MapContentCommonData::id).containsExactly(1L);
        verify(loader, times(0)).load(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertThat(meterRegistry.get("kgb.map.cache.size").gauge().value())
                .isEqualTo(1.0);
    }

    @Test
    void treatsMissingSnapshotTileAsEmptyWithoutDatabaseFallback() {
        when(loader.loadAllCacheable()).thenReturn(List.of());
        commonCache.refresh();

        assertThat(commonCache.get(37.401, 127.101, 37.409, 127.109)).isEmpty();
        assertThat(commonCache.get(37.402, 127.102, 37.408, 127.108)).isEmpty();

        verify(loader, times(0)).load(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertThat(meterRegistry.get("kgb.map.cache.size").gauge().value())
                .isZero();
    }

    @Test
    void mergesTilesWithoutDuplicatesAndFiltersActualBounds() {
        when(loader.loadAllCacheable()).thenReturn(List.of(
                        commonContent(2L, 37.505, 127.502),
                        commonContent(1L, 37.505, 127.498),
                        commonContent(3L, 37.505, 127.509)));
        commonCache.refresh();

        List<MapContentCommonData> result = commonCache.get(
                37.501, 127.495, 37.509, 127.505);

        assertThat(result).extracting(MapContentCommonData::id).containsExactly(1L, 2L);
        verify(loader, times(0)).load(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void reusesSameTileRegardlessOfDetailedZoomLevel() {
        when(loader.loadAllCacheable())
                .thenReturn(List.of(commonContent(1L, 37.405, 127.105)));
        commonCache.refresh();

        commonCache.get(37.401, 127.101, 37.409, 127.109);
        commonCache.get(37.401, 127.101, 37.409, 127.109);

        verify(loader, times(0)).load(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void readsWideBoundsFromSnapshotWithoutDatabaseFallback() {
        MapContentCommonData content = commonContent(1L, 37.5, 127.2);
        when(loader.loadAllCacheable()).thenReturn(List.of(content));
        commonCache.refresh();

        List<MapContentCommonData> first = commonCache.get(
                30.0, 120.0, 40.0, 135.0);
        List<MapContentCommonData> second = commonCache.get(
                30.0, 120.0, 40.0, 135.0);

        assertThat(first).containsExactly(content);
        assertThat(second).containsExactly(content);
        verify(loader, times(0)).load(30.0, 120.0, 40.0, 135.0);
    }

    @Test
    void rejectsRequestsUntilInitialSnapshotIsReady() {
        assertThatThrownBy(() -> commonCache.get(37.401, 127.101, 37.409, 127.109))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
        verify(loader, times(0)).load(anyDouble(), anyDouble(), anyDouble(), anyDouble());
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
    void refreshesOnlyTilesContainingChangedLocations(CapturedOutput output) {
        MapContentCommonData before = commonContent(1L, 37.405, 127.105);
        MapContentCommonData unchanged = commonContent(2L, 35.185, 129.075);
        when(loader.loadAllCacheable()).thenReturn(List.of(before, unchanged));
        commonCache.refresh();

        MapContentCommonData after = commonContent(1L, 37.605, 127.605);
        when(loader.load(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(invocation ->
                        ((double) invocation.getArgument(0)) < 37.5
                                ? List.of()
                                : List.of(after));

        commonCache.refreshByLocations(List.of(
                new MapContentCacheLocation(37.405, 127.105),
                new MapContentCacheLocation(37.605, 127.605)));

        assertThat(commonCache.get(37.401, 127.101, 37.409, 127.109)).isEmpty();
        assertThat(commonCache.get(37.601, 127.601, 37.609, 127.609))
                .containsExactly(after);
        assertThat(commonCache.get(35.181, 129.071, 35.189, 129.079))
                .containsExactly(unchanged);
        verify(loader, times(2)).load(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertThat(output).contains(
                "requestedLocations=2, affectedTiles=2, fallback=false");
    }

    @Test
    void refreshesChangedTileOnce() {
        MapContentCommonData before = commonContent(1L, 37.405, 127.105);
        when(loader.loadAllCacheable()).thenReturn(List.of(before));
        commonCache.refresh();

        MapContentCommonData after = commonContent(1L, 37.406, 127.106);
        when(loader.load(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(after));

        commonCache.refreshByLocations(List.of(
                new MapContentCacheLocation(37.405, 127.105)));

        assertThat(commonCache.get(37.401, 127.101, 37.409, 127.109))
                .containsExactly(after);
        verify(loader, times(1)).load(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void fallsBackToFullRefreshWhenInitialSnapshotIsNotReady(CapturedOutput output) {
        MapContentCommonData content = commonContent(1L, 37.405, 127.105);
        when(loader.loadAllCacheable()).thenReturn(List.of(content));

        commonCache.refreshByLocations(List.of(
                new MapContentCacheLocation(37.405, 127.105)));

        assertThat(commonCache.get(37.401, 127.101, 37.409, 127.109))
                .containsExactly(content);
        verify(loader, times(1)).loadAllCacheable();
        verify(loader, times(0)).load(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertThat(output).contains(
                "requestedLocations=1, affectedTiles=1, fallback=full");
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
