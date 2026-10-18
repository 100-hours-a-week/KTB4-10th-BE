package com.ktb10.kgb.content.tourapi;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TourApiSyncSchedulerTest {

    @Test
    void scheduledAndStartupExecutionUseSeoulBusinessDate() {
        TourApiSyncRunner runner = mock(TourApiSyncRunner.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-11T15:30:00Z"), ZoneOffset.UTC);
        TourApiSyncScheduler scheduler = new TourApiSyncScheduler(runner, clock);

        scheduler.recoverMissedRun();
        scheduler.runScheduled();
        scheduler.recoverStaleRun();

        verify(runner, times(2)).runIfDue(LocalDate.of(2026, 10, 12));
        verify(runner).recoverStaleIfDue(LocalDate.of(2026, 10, 12));
    }
}
