package com.ktb10.kgb.tools.tourapi;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** TourAPI 전국 목록 수집과 원본 스냅샷 저장 설정입니다. */
@ConfigurationProperties(prefix = "tour-api.collection")
public record TourApiCollectionProperties(
        URI baseUrl,
        String serviceKey,
        String snapshotLocation,
        String mobileOs,
        String mobileApp,
        int numOfRows,
        Duration connectTimeout,
        Duration readTimeout,
        int maxResponseBytes,
        int maxAttempts) {
}
