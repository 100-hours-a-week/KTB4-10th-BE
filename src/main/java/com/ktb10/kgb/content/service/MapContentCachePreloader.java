package com.ktb10.kgb.content.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 애플리케이션 시작 시 최신 관광 데이터를 상세 지도 캐시에 미리 적재합니다. */
@Component
@ConditionalOnProperty(
        name = "map.common-cache-preload-enabled",
        havingValue = "true")
public class MapContentCachePreloader implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapContentCachePreloader.class);

    private final MapContentCommonCache commonCache;

    public MapContentCachePreloader(MapContentCommonCache commonCache) {
        this.commonCache = commonCache;
    }

    @Override
    public void run(ApplicationArguments args) {
        commonCache.refresh();
        LOGGER.info("Map content cache preload completed");
    }
}
