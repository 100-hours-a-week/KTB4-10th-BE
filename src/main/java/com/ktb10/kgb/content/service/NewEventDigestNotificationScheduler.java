package com.ktb10.kgb.content.service;

import com.ktb10.kgb.content.tourapi.TourApiSyncRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 성공한 당일 TourAPI 동기화에서 서비스에 최초 적재된 행사를 알립니다. */
@Component
public class NewEventDigestNotificationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            NewEventDigestNotificationScheduler.class);
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final TourApiSyncRunRepository syncRunRepository;
    private final NewEventDigestNotificationService notificationService;
    private final NewEventDigestNotificationMetrics metrics;
    private final Clock clock;

    public NewEventDigestNotificationScheduler(
            TourApiSyncRunRepository syncRunRepository,
            NewEventDigestNotificationService notificationService,
            NewEventDigestNotificationMetrics metrics,
            Clock clock) {
        this.syncRunRepository = syncRunRepository;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${notification.new-events.cron}",
            zone = "Asia/Seoul")
    public void createDailyNotifications() {
        LocalDate syncDate = LocalDate.now(clock.withZone(SEOUL_ZONE));
        LocalDate referenceDate = syncDate;
        if (!syncRunRepository.hasSuccessfulRun(syncDate)) {
            metrics.recordSkipped();
            LOGGER.warn(
                    "신규 행사 묶음 알림 생성을 건너뜁니다. "
                            + "syncDate={}, referenceDate={}, reason=sync_not_completed",
                    syncDate,
                    referenceDate);
            return;
        }

        Instant startedAt = clock.instant();
        NewEventDigestNotificationSummary summary =
                notificationService.createForReferenceDate(referenceDate);
        Duration elapsed = Duration.between(startedAt, clock.instant());
        metrics.record(summary, elapsed);
        LOGGER.info(
                "신규 행사 묶음 알림 생성을 완료했습니다. "
                        + "syncDate={}, referenceDate={}, eventCount={}, targetMembers={}, "
                        + "created={}, duplicated={}, failed={}, durationMillis={}",
                syncDate,
                referenceDate,
                summary.newEventCount(),
                summary.targetMemberCount(),
                summary.createdCount(),
                summary.duplicateCount(),
                summary.failedCount(),
                elapsed.toMillis());
    }
}
