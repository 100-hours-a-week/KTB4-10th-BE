package com.ktb10.kgb.tools.loadtest;

import com.ktb10.kgb.common.observability.NotificationCreationPhase;
import com.ktb10.kgb.content.event.ContentChangesDetectedEvent;
import com.ktb10.kgb.content.service.NewEventDigestNotificationScheduler;
import com.ktb10.kgb.content.tourapi.TourApiContentChangeOccurrenceRepository;
import com.ktb10.kgb.content.tourapi.TourApiSyncRunRepository;
import com.ktb10.kgb.member.service.TripStartingNotificationScheduler;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 운영 cron을 기다리지 않고 실제 알림 생산자 진입점을 실행하는 로컬 전용 runner입니다. */
@Component
@Profile("local & loadtest")
public class NotificationFanoutLoadTestRunner {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final NewEventDigestNotificationScheduler newEventScheduler;
    private final TripStartingNotificationScheduler tripStartingScheduler;
    private final TourApiSyncRunRepository syncRunRepository;
    private final TourApiContentChangeOccurrenceRepository occurrenceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public NotificationFanoutLoadTestRunner(
            NewEventDigestNotificationScheduler newEventScheduler,
            TripStartingNotificationScheduler tripStartingScheduler,
            TourApiSyncRunRepository syncRunRepository,
            TourApiContentChangeOccurrenceRepository occurrenceRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.newEventScheduler = newEventScheduler;
        this.tripStartingScheduler = tripStartingScheduler;
        this.syncRunRepository = syncRunRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public RunResult run(
            NotificationFanoutLoadTestScenario scenario, NotificationCreationPhase phase) {
        LocalDate syncDate = LocalDate.now(clock.withZone(SEOUL_ZONE));
        UUID runId =
                syncRunRepository
                        .findSuccessfulRunId(syncDate)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "오늘 성공한 TourAPI 동기화 fixture가 없습니다."));

        if (scenario == NotificationFanoutLoadTestScenario.NEW_EVENT_DIGEST
                || scenario == NotificationFanoutLoadTestScenario.MIXED) {
            runNewEvent(phase);
        }
        if (scenario == NotificationFanoutLoadTestScenario.INTERESTED_EVENT_CHANGE
                || scenario == NotificationFanoutLoadTestScenario.GUIDEBOOK_EVENT_CHANGE
                || scenario == NotificationFanoutLoadTestScenario.MIXED) {
            int occurrenceCount = occurrenceRepository.countByRunId(runId);
            eventPublisher.publishEvent(
                    new ContentChangesDetectedEvent(runId, syncDate, occurrenceCount, phase));
        }
        if (scenario == NotificationFanoutLoadTestScenario.TRIP_STARTING
                || scenario == NotificationFanoutLoadTestScenario.MIXED) {
            runTripStarting(phase);
        }
        return new RunResult(scenario, phase, runId, syncDate);
    }

    private void runNewEvent(NotificationCreationPhase phase) {
        if (phase == NotificationCreationPhase.INITIAL) {
            newEventScheduler.createDailyNotifications();
        } else {
            newEventScheduler.reconcileDailyNotifications();
        }
    }

    private void runTripStarting(NotificationCreationPhase phase) {
        if (phase == NotificationCreationPhase.INITIAL) {
            tripStartingScheduler.createTomorrowNotifications();
        } else {
            tripStartingScheduler.reconcileTomorrowNotifications();
        }
    }

    public record RunResult(
            NotificationFanoutLoadTestScenario scenario,
            NotificationCreationPhase phase,
            UUID runId,
            LocalDate syncDate) {}
}
