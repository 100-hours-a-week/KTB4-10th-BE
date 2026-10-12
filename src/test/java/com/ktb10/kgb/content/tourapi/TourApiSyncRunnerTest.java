package com.ktb10.kgb.content.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.content.service.MapContentCacheRefreshService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TourApiSyncRunnerTest {

    private static final LocalDate SYNC_DATE = LocalDate.of(2026, 10, 12);

    private final TourApiSyncService syncService = mock(TourApiSyncService.class);
    private final TourApiSyncRunRepository runRepository = mock(TourApiSyncRunRepository.class);
    private final MapContentCacheRefreshService cacheRefreshService =
            mock(MapContentCacheRefreshService.class);
    private TourApiSyncRunner runner;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-12T01:00:00Z"), ZoneOffset.UTC);
        TourApiSchedulerProperties properties = new TourApiSchedulerProperties(
                true,
                3,
                Duration.ZERO,
                Duration.ofHours(2));
        runner = new TourApiSyncRunner(
                syncService,
                runRepository,
                cacheRefreshService,
                properties,
                clock);
    }

    @Test
    void skipsWhenAnotherInstanceOwnsTheDate() {
        when(runRepository.tryStart(any(), any(), any(), any())).thenReturn(false);

        assertThat(runner.runIfDue(SYNC_DATE)).isFalse();

        verify(syncService, never()).synchronize();
    }

    @Test
    void recordsSuccessfulRun() {
        ImportSummary summary = new ImportSummary(10, 2, 0, 0, 1, 1, 3, 1, 0);
        when(runRepository.tryStart(any(), any(), any(), any())).thenReturn(true);
        when(syncService.synchronize()).thenReturn(summary);

        assertThat(runner.runIfDue(SYNC_DATE)).isTrue();

        verify(runRepository).complete(any(UUID.class), any(ImportSummary.class), any());
        verify(cacheRefreshService).refreshAfterFullSync();
        verify(runRepository, never()).fail(any(), any(), any(), any());
    }

    @Test
    void retriesRetryableFailureAndThenSucceeds() {
        ImportSummary summary = new ImportSummary(10, 2, 0, 0, 0, 0, 3, 1, 0);
        when(runRepository.tryStart(any(), any(), any(), any())).thenReturn(true);
        when(syncService.synchronize())
                .thenThrow(new TourApiException("temporary", true))
                .thenReturn(summary);

        assertThat(runner.runIfDue(SYNC_DATE)).isTrue();

        verify(syncService, times(2)).synchronize();
        verify(runRepository).recordAttempt(any(), org.mockito.ArgumentMatchers.eq(2), any());
    }

    @Test
    void recordsFailureWithoutRetryForPermanentError() {
        when(runRepository.tryStart(any(), any(), any(), any())).thenReturn(true);
        when(syncService.synchronize())
                .thenThrow(new TourApiException("invalid configuration", false));

        assertThat(runner.runIfDue(SYNC_DATE)).isFalse();

        verify(syncService).synchronize();
        verify(runRepository).fail(
                any(),
                org.mockito.ArgumentMatchers.eq("SYNCHRONIZE"),
                any(TourApiException.class),
                any());
        verify(runRepository, never()).complete(any(), any(), any());
    }
}
