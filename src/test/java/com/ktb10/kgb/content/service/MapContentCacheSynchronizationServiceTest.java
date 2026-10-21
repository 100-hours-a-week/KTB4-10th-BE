package com.ktb10.kgb.content.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.content.tourapi.TourApiCacheRefreshRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class MapContentCacheSynchronizationServiceTest {

    private final MapContentCommonCache commonCache = Mockito.mock(MapContentCommonCache.class);
    private final TourApiCacheRefreshRepository repository =
            Mockito.mock(TourApiCacheRefreshRepository.class);
    private final MapContentCacheSynchronizationService service =
            new MapContentCacheSynchronizationService(commonCache, repository);

    @Test
    void preloadCapturesBaselineBeforeBuildingSnapshot() {
        when(repository.findLatestBatchId()).thenReturn(7L);

        service.preload();
        service.applyAvailableRefreshes();

        InOrder order = Mockito.inOrder(repository, commonCache);
        order.verify(repository).findLatestBatchId();
        order.verify(commonCache).refresh();
        verify(repository, never()).findTilesAfter(7L, 7L);
    }

    @Test
    void appliesAllSuccessfulTilesSinceLastLocalVersion() {
        MapContentCacheTile first = new MapContentCacheTile(74, 254);
        MapContentCacheTile second = new MapContentCacheTile(75, 255);
        when(repository.findLatestBatchId()).thenReturn(3L, 5L, 5L);
        when(repository.findTilesAfter(3L, 5L))
                .thenReturn(List.of(first, second));

        service.preload();
        service.applyAvailableRefreshes();
        service.applyAvailableRefreshes();

        verify(commonCache).refreshByTiles(List.of(first, second));
        verify(repository).findTilesAfter(3L, 5L);
    }
}
