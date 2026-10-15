package com.ktb10.kgb.content.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MapContentCacheRefreshServiceTest {

    private final MapContentCommonCache commonCache = mock(MapContentCommonCache.class);
    private final MapContentCommonLoader loader = mock(MapContentCommonLoader.class);
    private final MapContentCacheRefreshService service =
            new MapContentCacheRefreshService(commonCache, loader);

    @Test
    void refreshesChangedAndExpiredEventLocationsWithoutDuplicates() {
        LocalDate syncDate = LocalDate.of(2026, 10, 15);
        MapContentCacheLocation changed = new MapContentCacheLocation(37.5, 127.0);
        MapContentCacheLocation expired = new MapContentCacheLocation(35.1, 129.0);
        when(loader.loadEventLocationsEndingOn(LocalDate.of(2026, 10, 14)))
                .thenReturn(List.of(changed, expired));

        service.refreshAfterBatch(List.of(changed), syncDate);

        verify(loader).loadEventLocationsEndingOn(LocalDate.of(2026, 10, 14));
        verify(commonCache).refreshByLocations(List.of(changed, expired));
    }
}
