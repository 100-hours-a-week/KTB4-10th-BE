package com.ktb10.kgb.content.service;

import com.ktb10.kgb.content.repository.MapContentCommonData;
import com.ktb10.kgb.content.repository.MapContentQuery;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 캐시 미스 시 공통 지도 데이터를 읽기 전용 트랜잭션으로 조회합니다. */
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

}
