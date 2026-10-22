package com.ktb10.kgb.content.tourapi;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 운영 DB의 기존 TourAPI 콘텐츠를 상세 API로 나눠 재적재할 때 사용하는 설정입니다. */
@ConfigurationProperties(prefix = "tour-api.detail-backfill")
public record TourApiDetailBackfillProperties(
        boolean enabled,
        int batchSize,
        int maxBatches,
        long startAfterId,
        Duration requestDelay) {

    public TourApiDetailBackfillProperties {
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("detail backfill batch-size는 1~1000이어야 합니다.");
        }
        if (maxBatches < 1) {
            throw new IllegalArgumentException("detail backfill max-batches는 1 이상이어야 합니다.");
        }
        if (startAfterId < 0) {
            throw new IllegalArgumentException("detail backfill start-after-id는 0 이상이어야 합니다.");
        }
        if (requestDelay == null || requestDelay.isNegative()) {
            throw new IllegalArgumentException("detail backfill request-delay는 0 이상이어야 합니다.");
        }
    }
}
