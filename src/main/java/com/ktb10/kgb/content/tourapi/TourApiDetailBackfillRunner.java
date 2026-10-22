package com.ktb10.kgb.content.tourapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 명시적으로 활성화했을 때만 상세 backfill 배치를 실행하는 일회성 러너입니다. */
@Component
@ConditionalOnProperty(
        prefix = "tour-api.detail-backfill",
        name = "enabled",
        havingValue = "true")
public class TourApiDetailBackfillRunner implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TourApiDetailBackfillRunner.class);

    private final TourApiDetailBackfillService service;
    private final TourApiDetailBackfillProperties properties;

    public TourApiDetailBackfillRunner(
            TourApiDetailBackfillService service,
            TourApiDetailBackfillProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        long afterId = properties.startAfterId();
        int totalTargets = 0;
        int totalCompleted = 0;
        int totalFailed = 0;

        for (int batch = 1; batch <= properties.maxBatches(); batch++) {
            TourApiDetailBackfillService.BackfillBatchResult result = service.runBatch(
                    afterId,
                    properties.batchSize(),
                    properties.requestDelay());
            totalTargets += result.targets();
            totalCompleted += result.completed();
            totalFailed += result.failedSourceContentIds().size();
            afterId = result.nextAfterId();

            LOGGER.info(
                    "TourAPI detail backfill progress: batch={}/{}, nextAfterId={}, "
                            + "batchTargets={}, batchFailed={}",
                    batch,
                    properties.maxBatches(),
                    afterId,
                    result.targets(),
                    result.failedSourceContentIds());
            if (!result.hasMore()) {
                break;
            }
        }

        LOGGER.info(
                "TourAPI detail backfill run finished: nextAfterId={}, targets={}, "
                        + "completed={}, failed={}",
                afterId,
                totalTargets,
                totalCompleted,
                totalFailed);
    }
}
