package com.ktb10.kgb.content.tourapi;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** TourAPI 정기 동기화 실행과 실패 복구 설정입니다. */
@ConfigurationProperties(prefix = "tour-api.scheduler")
public record TourApiSchedulerProperties(
        boolean enabled,
        int maxAttempts,
        Duration retryBackoff,
        Duration staleRunTimeout) {
}
