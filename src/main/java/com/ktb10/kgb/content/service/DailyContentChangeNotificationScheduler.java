package com.ktb10.kgb.content.service;

import com.ktb10.kgb.common.observability.NotificationCreationPhase;
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

/** 성공한 당일 TourAPI 변경 이력을 최초 실행과 제한 재실행 알림 배치로 전달합니다. */
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
        dispatchSuccessfulRun(today(), NotificationCreationPhase.INITIAL);
    }

    @Scheduled(
            cron = "${notification.reconciliation.cron}",
            zone = "Asia/Seoul")
    public void reconcileDailyNotifications() {
        dispatchSuccessfulRun(today(), NotificationCreationPhase.RECONCILIATION);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverMissedSchedule() {
        if (!seoulTime().isBefore(DELIVERY_TIME)) {
            dispatchSuccessfulRun(today(), NotificationCreationPhase.INITIAL);
        }
    }

    @EventListener
    public void recoverDelayedSync(TourApiSyncCompletedEvent event) {
        if (event.syncDate().equals(today()) && !seoulTime().isBefore(DELIVERY_TIME)) {
            dispatch(event.runId(), event.syncDate(), NotificationCreationPhase.INITIAL);
        }
    }

    private void dispatchSuccessfulRun(LocalDate syncDate, NotificationCreationPhase phase) {
        syncRunRepository.findSuccessfulRunId(syncDate)
                .ifPresentOrElse(
                        runId -> dispatch(runId, syncDate, phase),
                        () -> skipIncompleteSync(syncDate, phase));
    }

    private void dispatch(
            UUID runId,
            LocalDate syncDate,
            NotificationCreationPhase phase) {
        int occurrenceCount = occurrenceRepository.countByRunId(runId);
        if (occurrenceCount == 0) {
            LOGGER.info(
                    "일일 행사 변경 알림 대상 이력이 없습니다. "
                            + "phase={}, runId={}, syncDate={}",
                    phase.tagValue(),
                    runId,
                    syncDate);
            return;
        }
        eventPublisher.publishEvent(new ContentChangesDetectedEvent(
                runId,
                syncDate,
                occurrenceCount,
                phase));
    }

    private void skipIncompleteSync(LocalDate syncDate, NotificationCreationPhase phase) {
        metrics.recordSyncNotCompleted();
        LOGGER.warn(
                "일일 행사 변경 알림 생성을 건너뜁니다. "
                        + "phase={}, syncDate={}, reason=sync_not_completed",
                phase.tagValue(),
                syncDate);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(SEOUL_ZONE));
    }

    private LocalTime seoulTime() {
        return LocalTime.now(clock.withZone(SEOUL_ZONE));
    }
}
