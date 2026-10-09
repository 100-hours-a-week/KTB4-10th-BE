package com.ktb10.kgb.content.service;

import java.util.Collection;
import org.springframework.stereotype.Service;

/** 일일 관광 데이터 배치가 완료된 뒤 변경된 지도 타일을 갱신합니다. */
@Service
public class MapContentCacheRefreshService {

    private final MapContentCommonCache commonCache;

    public MapContentCacheRefreshService(MapContentCommonCache commonCache) {
        this.commonCache = commonCache;
    }

    /** 배치가 수집한 변경 전·후 좌표의 타일만 갱신합니다. */
    public void refreshAfterBatch(Collection<MapContentCacheLocation> changedLocations) {
        commonCache.refreshByLocations(changedLocations);
    }
}
