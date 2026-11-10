package com.ktb10.kgb.member.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.common.observability.NotificationCreationBatchMetrics;
import com.ktb10.kgb.common.observability.NotificationCreationPhase;
import com.ktb10.kgb.common.observability.NotificationCreationProducer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripStartingNotificationSchedulerTest {

    @Mock
    private TripStartingNotificationService notificationService;

    @Mock
    private NotificationCreationBatchMetrics batchMetrics;

    @Test
    void requestsTomorrowBasedOnSeoulDate() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-16T23:30:00Z"),
                ZoneOffset.UTC);
        TripStartingNotificationScheduler scheduler =
                new TripStartingNotificationScheduler(notificationService, batchMetrics, clock);
        given(notificationService.createForStartDate(LocalDate.of(2026, 10, 18)))
                .willReturn(new TripStartingNotificationSummary(0, 0, 0));

        scheduler.createTomorrowNotifications();

        verify(notificationService).createForStartDate(LocalDate.of(2026, 10, 18));
        verify(batchMetrics).record(
                NotificationCreationProducer.TRIP_STARTING,
                NotificationCreationPhase.INITIAL,
                0,
                0,
                0,
                0,
                Duration.ZERO);
    }

    @Test
    void rerunsTomorrowAtReconciliationTime() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-16T23:30:00Z"),
                ZoneOffset.UTC);
        TripStartingNotificationScheduler scheduler =
                new TripStartingNotificationScheduler(notificationService, batchMetrics, clock);
        given(notificationService.createForStartDate(LocalDate.of(2026, 10, 18)))
                .willReturn(new TripStartingNotificationSummary(0, 1, 0));

        scheduler.reconcileTomorrowNotifications();

        verify(batchMetrics).record(
                NotificationCreationProducer.TRIP_STARTING,
                NotificationCreationPhase.RECONCILIATION,
                1,
                0,
                1,
                0,
                Duration.ZERO);
    }
}
