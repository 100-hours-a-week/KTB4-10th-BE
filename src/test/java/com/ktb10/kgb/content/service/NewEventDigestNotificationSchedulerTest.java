package com.ktb10.kgb.content.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.content.tourapi.TourApiSyncRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewEventDigestNotificationSchedulerTest {

    private static final LocalDate SYNC_DATE = LocalDate.of(2026, 11, 3);
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 11, 2);

    @Mock
    private TourApiSyncRunRepository syncRunRepository;

    @Mock
    private NewEventDigestNotificationService notificationService;

    @Mock
    private NewEventDigestNotificationMetrics metrics;

    private NewEventDigestNotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-11-03T00:00:00Z"),
                ZoneOffset.UTC);
        scheduler = new NewEventDigestNotificationScheduler(
                syncRunRepository,
                notificationService,
                metrics,
                clock);
    }

    @Test
    void createsNotificationsForPreviousDayAfterSuccessfulSync() {
        NewEventDigestNotificationSummary summary =
                new NewEventDigestNotificationSummary(3L, 2, 2, 0, 0);
        given(syncRunRepository.hasSuccessfulRun(SYNC_DATE)).willReturn(true);
        given(notificationService.createForReferenceDate(REFERENCE_DATE))
                .willReturn(summary);

        scheduler.createDailyNotifications();

        verify(notificationService).createForReferenceDate(REFERENCE_DATE);
        verify(metrics).record(summary, Duration.ZERO);
    }

    @Test
    void skipsNotificationsUntilDailySyncCompletes() {
        given(syncRunRepository.hasSuccessfulRun(SYNC_DATE)).willReturn(false);

        scheduler.createDailyNotifications();

        verify(notificationService, never()).createForReferenceDate(REFERENCE_DATE);
        verify(metrics).recordSkipped();
    }
}
