package com.ktb10.kgb.content.service;

import com.ktb10.kgb.content.event.ContentChangesDetectedEvent;
import com.ktb10.kgb.content.event.TourApiSyncCompletedEvent;
import com.ktb10.kgb.content.tourapi.TourApiContentChangeOccurrenceRepository;
import com.ktb10.kgb.content.tourapi.TourApiSyncRunRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 성공한 당일 TourAPI 변경 이력을 오전 9시 행사 알림 배치로 전달합니다. */
@Component
@ConditionalOnProperty(prefix = "tour-api.scheduler", name = "enabled", havingValue = "true")
public class DailyContentChangeNotificationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            DailyContentChangeNotificationScheduler.class);
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalTime DELIVERY_TIME = LocalTime.of(9, 0);

    private final TourApiSyncRunRepository syncRunRepository;
    private final TourApiContentChangeOccurrenceRepository occurrenceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ContentChangeNotificationBatchMetrics metrics;
    private final Clock clock;

    public DailyContentChangeNotificationScheduler(
            TourApiSyncRunRepository syncRunRepository,
            TourApiContentChangeOccurrenceRepository occurrenceRepository,
            ApplicationEventPublisher eventPublisher,
            ContentChangeNotificationBatchMetrics metrics,
            Clock clock) {
        this.syncRunRepository = syncRunRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${notification.content-changes.cron}",
            zone = "Asia/Seoul")
    public void createDailyNotifications() {
        dispatchSuccessfulRun(today());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverMissedSchedule() {
        if (!seoulTime().isBefore(DELIVERY_TIME)) {
            dispatchSuccessfulRun(today());
        }
    }

    @EventListener
    public void recoverDelayedSync(TourApiSyncCompletedEvent event) {
        if (event.syncDate().equals(today()) && !seoulTime().isBefore(DELIVERY_TIME)) {
            dispatch(event.runId(), event.syncDate());
        }
    }

    private void dispatchSuccessfulRun(LocalDate syncDate) {
        syncRunRepository.findSuccessfulRunId(syncDate)
                .ifPresentOrElse(
                        runId -> dispatch(runId, syncDate),
                        () -> skipIncompleteSync(syncDate));
    }

    private void dispatch(UUID runId, LocalDate syncDate) {
        int occurrenceCount = occurrenceRepository.countByRunId(runId);
        if (occurrenceCount == 0) {
            LOGGER.info(
                    "오전 9시 행사 변경 알림 대상 이력이 없습니다. "
                            + "runId={}, syncDate={}",
                    runId,
                    syncDate);
            return;
        }
        eventPublisher.publishEvent(new ContentChangesDetectedEvent(
                runId,
                syncDate,
                occurrenceCount));
    }

    private void skipIncompleteSync(LocalDate syncDate) {
        metrics.recordSyncNotCompleted();
        LOGGER.warn(
                "오전 9시 행사 변경 알림 생성을 건너뜁니다. "
                        + "syncDate={}, reason=sync_not_completed",
                syncDate);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(SEOUL_ZONE));
    }

    private LocalTime seoulTime() {
        return LocalTime.now(clock.withZone(SEOUL_ZONE));
    }
}
