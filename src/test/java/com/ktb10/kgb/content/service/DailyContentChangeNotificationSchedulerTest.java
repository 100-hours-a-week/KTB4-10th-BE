package com.ktb10.kgb.content.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.common.observability.NotificationCreationPhase;
import com.ktb10.kgb.content.event.ContentChangesDetectedEvent;
import com.ktb10.kgb.content.event.TourApiSyncCompletedEvent;
import com.ktb10.kgb.content.tourapi.TourApiContentChangeOccurrenceRepository;
import com.ktb10.kgb.content.tourapi.TourApiSyncRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class DailyContentChangeNotificationSchedulerTest {

    private static final UUID RUN_ID =
            UUID.fromString("c783905d-904f-41b9-8016-cb8242533cc8");
    private static final LocalDate SYNC_DATE = LocalDate.of(2026, 11, 3);

    private final TourApiSyncRunRepository syncRunRepository =
            org.mockito.Mockito.mock(TourApiSyncRunRepository.class);
    private final TourApiContentChangeOccurrenceRepository occurrenceRepository =
            org.mockito.Mockito.mock(TourApiContentChangeOccurrenceRepository.class);
    private final ApplicationEventPublisher eventPublisher =
            org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    private final ContentChangeNotificationBatchMetrics metrics =
            org.mockito.Mockito.mock(ContentChangeNotificationBatchMetrics.class);

    @Test
    void publishesDailyEventAtNineAfterSuccessfulSync() {
        DailyContentChangeNotificationScheduler scheduler = schedulerAt("2026-11-03T00:00:00Z");
        given(syncRunRepository.findSuccessfulRunId(SYNC_DATE))
                .willReturn(Optional.of(RUN_ID));
        given(occurrenceRepository.countByRunId(RUN_ID)).willReturn(2);

        scheduler.createDailyNotifications();

        verify(eventPublisher).publishEvent(new ContentChangesDetectedEvent(
                RUN_ID, SYNC_DATE, 2));
    }

    @Test
    void skipsWhenDailySyncHasNotCompleted() {
        DailyContentChangeNotificationScheduler scheduler = schedulerAt("2026-11-03T00:00:00Z");
        given(syncRunRepository.findSuccessfulRunId(SYNC_DATE)).willReturn(Optional.empty());

        scheduler.createDailyNotifications();

        verify(metrics).recordSyncNotCompleted();
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishesReconciliationEventForSameSuccessfulRun() {
        DailyContentChangeNotificationScheduler scheduler = schedulerAt("2026-11-03T03:00:00Z");
        given(syncRunRepository.findSuccessfulRunId(SYNC_DATE))
                .willReturn(Optional.of(RUN_ID));
        given(occurrenceRepository.countByRunId(RUN_ID)).willReturn(2);

        scheduler.reconcileDailyNotifications();

        verify(eventPublisher).publishEvent(new ContentChangesDetectedEvent(
                RUN_ID, SYNC_DATE, 2, NotificationCreationPhase.RECONCILIATION));
    }

    @Test
    void delayedSyncAfterNineTriggersSameDailyProcessing() {
        DailyContentChangeNotificationScheduler scheduler = schedulerAt("2026-11-03T01:00:00Z");
        given(occurrenceRepository.countByRunId(RUN_ID)).willReturn(1);

        scheduler.recoverDelayedSync(new TourApiSyncCompletedEvent(RUN_ID, SYNC_DATE));

        verify(eventPublisher).publishEvent(new ContentChangesDetectedEvent(
                RUN_ID, SYNC_DATE, 1));
    }

    @Test
    void syncBeforeNineDoesNotSendNotificationsImmediately() {
        DailyContentChangeNotificationScheduler scheduler = schedulerAt("2026-11-02T23:59:00Z");

        scheduler.recoverDelayedSync(new TourApiSyncCompletedEvent(RUN_ID, SYNC_DATE));

        verify(occurrenceRepository, never()).countByRunId(RUN_ID);
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    private DailyContentChangeNotificationScheduler schedulerAt(String instant) {
        return new DailyContentChangeNotificationScheduler(
                syncRunRepository,
                occurrenceRepository,
                eventPublisher,
                metrics,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
