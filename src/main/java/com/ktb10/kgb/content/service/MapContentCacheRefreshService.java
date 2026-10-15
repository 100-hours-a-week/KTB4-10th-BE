package com.ktb10.kgb.content.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/** 일일 관광 데이터 배치가 완료된 뒤 변경된 지도 타일을 갱신합니다. */
@Service
public class MapContentCacheRefreshService {

    private final MapContentCommonCache commonCache;
    private final MapContentCommonLoader loader;

    public MapContentCacheRefreshService(
            MapContentCommonCache commonCache,
            MapContentCommonLoader loader) {
        this.commonCache = commonCache;
        this.loader = loader;
    }

    /** 배치 변경 좌표와 전날 종료된 행사의 타일만 갱신합니다. */
    public void refreshAfterBatch(
            Collection<MapContentCacheLocation> changedLocations,
            LocalDate syncDate) {
        Collection<MapContentCacheLocation> expiredEventLocations =
                loader.loadEventLocationsEndingOn(syncDate.minusDays(1));
        commonCache.refreshByLocations(Stream.concat(
                        changedLocations.stream(),
                        expiredEventLocations.stream())
                .distinct()
                .toList());
    }

    /** 변경 위치 목록을 제공하지 않는 전체 동기화가 끝나면 캐시 전체를 다시 구성합니다. */
    public void refreshAfterFullSync() {
        commonCache.refresh();
    }
}
