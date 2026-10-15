package com.ktb10.kgb.content.service;

import com.ktb10.kgb.content.repository.MapContentCommonData;
import com.ktb10.kgb.content.repository.MapContentQuery;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지도 스냅샷 전체 또는 배치로 변경된 타일을 DB에서 적재합니다. */
@Service
public class MapContentCommonLoader {

    private final MapContentQuery mapContentQuery;

    public MapContentCommonLoader(MapContentQuery mapContentQuery) {
        this.mapContentQuery = mapContentQuery;
    }

    @Transactional(readOnly = true)
    public List<MapContentCommonData> load(
            double south,
            double west,
            double north,
            double east) {
        return List.copyOf(mapContentQuery.findWithinBounds(south, west, north, east));
    }

    @Transactional(readOnly = true)
    public List<MapContentCommonData> loadAllCacheable() {
        return List.copyOf(mapContentQuery.findAllCacheable());
    }

    @Transactional(readOnly = true)
    public List<MapContentCacheLocation> loadEventLocationsEndingOn(LocalDate endDate) {
        return mapContentQuery.findEventsEndingOn(endDate).stream()
                .map(content -> new MapContentCacheLocation(
                        content.latitude(),
                        content.longitude()))
                .toList();
    }

}
