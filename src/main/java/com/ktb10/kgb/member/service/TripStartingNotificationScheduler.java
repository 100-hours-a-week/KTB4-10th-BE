package com.ktb10.kgb.member.service;

import com.ktb10.kgb.common.observability.NotificationCreationBatchMetrics;
import com.ktb10.kgb.common.observability.NotificationCreationPhase;
import com.ktb10.kgb.common.observability.NotificationCreationProducer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Asia/Seoul 기준 오전 9시에 다음 날 시작하는 여행 알림을 생성합니다. */
@Component
public class TripStartingNotificationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            TripStartingNotificationScheduler.class);
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final TripStartingNotificationService notificationService;
    private final NotificationCreationBatchMetrics batchMetrics;
    private final Clock clock;

    public TripStartingNotificationScheduler(
            TripStartingNotificationService notificationService,
            NotificationCreationBatchMetrics batchMetrics,
            Clock clock) {
        this.notificationService = notificationService;
        this.batchMetrics = batchMetrics;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${notification.trip-starting.cron}",
            zone = "Asia/Seoul")
    public void createTomorrowNotifications() {
        executeTomorrowNotifications(NotificationCreationPhase.INITIAL);
    }

    @Scheduled(
            cron = "${notification.reconciliation.cron}",
            zone = "Asia/Seoul")
    public void reconcileTomorrowNotifications() {
        executeTomorrowNotifications(NotificationCreationPhase.RECONCILIATION);
    }

    private void executeTomorrowNotifications(NotificationCreationPhase phase) {
        LocalDate startDate = LocalDate.now(clock.withZone(SEOUL_ZONE)).plusDays(1);
        Instant startedAt = clock.instant();
        TripStartingNotificationSummary summary =
                notificationService.createForStartDate(startDate);
        Duration elapsed = Duration.between(startedAt, clock.instant());
        int targets = summary.created() + summary.duplicated() + summary.failed();
        batchMetrics.record(
                NotificationCreationProducer.TRIP_STARTING,
                phase,
                targets,
                summary.created(),
                summary.duplicated(),
                summary.failed(),
                elapsed);
        LOGGER.info(
                "여행 일정 임박 알림 생성을 완료했습니다. "
                        + "producer={}, phase={}, startDate={}, targetMembers={}, "
                        + "created={}, duplicated={}, failed={}, durationMillis={}",
                NotificationCreationProducer.TRIP_STARTING.tagValue(),
                phase.tagValue(),
                startDate,
                targets,
                summary.created(),
                summary.duplicated(),
                summary.failed(),
                elapsed.toMillis());
    }
}
