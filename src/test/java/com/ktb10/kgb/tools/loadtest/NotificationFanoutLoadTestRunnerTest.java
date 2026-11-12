package com.ktb10.kgb.tools.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.common.observability.NotificationCreationPhase;
import com.ktb10.kgb.content.event.ContentChangesDetectedEvent;
import com.ktb10.kgb.content.service.NewEventDigestNotificationScheduler;
import com.ktb10.kgb.content.tourapi.TourApiContentChangeOccurrenceRepository;
import com.ktb10.kgb.content.tourapi.TourApiSyncRunRepository;
import com.ktb10.kgb.member.service.TripStartingNotificationScheduler;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

class NotificationFanoutLoadTestRunnerTest {

    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000346");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneOffset.UTC);

    private final NewEventDigestNotificationScheduler newEventScheduler =
            mock(NewEventDigestNotificationScheduler.class);
    private final TripStartingNotificationScheduler tripStartingScheduler =
            mock(TripStartingNotificationScheduler.class);
    private final TourApiSyncRunRepository syncRunRepository = mock(TourApiSyncRunRepository.class);
    private final TourApiContentChangeOccurrenceRepository occurrenceRepository =
            mock(TourApiContentChangeOccurrenceRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final NotificationFanoutLoadTestRunner runner =
            new NotificationFanoutLoadTestRunner(
                    newEventScheduler,
                    tripStartingScheduler,
                    syncRunRepository,
                    occurrenceRepository,
                    eventPublisher,
                    CLOCK);

    @Test
    void runsAllMixedInitialProducersAgainstSameSyncRun() {
        given(syncRunRepository.findSuccessfulRunId(LocalDate.of(2026, 11, 10)))
                .willReturn(Optional.of(RUN_ID));
        given(occurrenceRepository.countByRunId(RUN_ID)).willReturn(1);

        NotificationFanoutLoadTestRunner.RunResult result =
                runner.run(
                        NotificationFanoutLoadTestScenario.MIXED,
                        NotificationCreationPhase.INITIAL);

        verify(newEventScheduler).createDailyNotifications();
        verify(tripStartingScheduler).createTomorrowNotifications();
        ArgumentCaptor<ContentChangesDetectedEvent> eventCaptor =
                ArgumentCaptor.forClass(ContentChangesDetectedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().runId()).isEqualTo(RUN_ID);
        assertThat(eventCaptor.getValue().occurrenceCount()).isEqualTo(1);
        assertThat(eventCaptor.getValue().phase()).isEqualTo(NotificationCreationPhase.INITIAL);
        assertThat(result.runId()).isEqualTo(RUN_ID);
    }

    @Test
    void runsOnlyTripStartingReconciliationForTripScenario() {
        given(syncRunRepository.findSuccessfulRunId(LocalDate.of(2026, 11, 10)))
                .willReturn(Optional.of(RUN_ID));

        runner.run(
                NotificationFanoutLoadTestScenario.TRIP_STARTING,
                NotificationCreationPhase.RECONCILIATION);

        verify(tripStartingScheduler).reconcileTomorrowNotifications();
    }
}
