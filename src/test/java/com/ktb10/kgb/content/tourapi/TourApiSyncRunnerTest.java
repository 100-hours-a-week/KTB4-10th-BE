package com.ktb10.kgb.content.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.content.event.InterestedContentChangesDetectedEvent;
import com.ktb10.kgb.content.service.MapContentCacheSynchronizationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TourApiSyncRunnerTest {

    private static final LocalDate SYNC_DATE = LocalDate.of(2026, 10, 12);

    private final TourApiSyncService syncService = mock(TourApiSyncService.class);
    private final TourApiSyncRunRepository runRepository = mock(TourApiSyncRunRepository.class);
    private final MapContentCacheSynchronizationService cacheSynchronizationService =
            mock(MapContentCacheSynchronizationService.class);
    private final InterestedContentChangeCandidateSelector notificationCandidateSelector =
            mock(InterestedContentChangeCandidateSelector.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
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
                cacheSynchronizationService,
                notificationCandidateSelector,
                eventPublisher,
                properties,
                clock);
    }

    @Test
    void publishesInterestedContentChangesAfterSuccessfulRun() {
        ContentChangeSet changeSet = ContentChangeSet.of(
                "100",
                java.util.Set.of(ContentChangedField.DESCRIPTION),
                ContentLifecycleChange.NONE,
                null,
                null);
        InterestedContentChangeCandidate candidate =
                new InterestedContentChangeCandidate(
                        "100",
                        java.util.Set.of(ContentChangedField.DESCRIPTION));
        ImportSummary summary = new ImportSummary(
                10, 1, 0, 0, 0, 0, 0, 0, java.util.List.of(changeSet));
        when(runRepository.tryStart(any(), any(), any(), any())).thenReturn(true);
        when(syncService.synchronize(any(UUID.class), eq(SYNC_DATE))).thenReturn(summary);
        when(notificationCandidateSelector.select(summary.changeSets()))
                .thenReturn(java.util.List.of(candidate));

        assertThat(runner.runIfDue(SYNC_DATE)).isTrue();

        verify(runRepository).complete(any(UUID.class), any(ImportSummary.class), any());
        verify(eventPublisher).publishEvent(new InterestedContentChangesDetectedEvent(
                SYNC_DATE,
                java.util.List.of(candidate)));
    }

    @Test
    void doesNotPublishEventWhenNoNotificationCandidateExists() {
        ImportSummary summary = new ImportSummary(10, 1, 0, 0, 0, 0, 0, 0);
        when(runRepository.tryStart(any(), any(), any(), any())).thenReturn(true);
        when(syncService.synchronize(any(UUID.class), eq(SYNC_DATE))).thenReturn(summary);
        when(notificationCandidateSelector.select(summary.changeSets()))
                .thenReturn(java.util.List.of());

        assertThat(runner.runIfDue(SYNC_DATE)).isTrue();

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void keepsSuccessfulSyncWhenNotificationDispatchFails() {
        ImportSummary summary = new ImportSummary(10, 1, 0, 0, 0, 0, 0, 0);
        when(runRepository.tryStart(any(), any(), any(), any())).thenReturn(true);
        when(syncService.synchronize(any(UUID.class), eq(SYNC_DATE))).thenReturn(summary);
        when(notificationCandidateSelector.select(summary.changeSets()))
                .thenThrow(new IllegalStateException("알림 후보 선별 실패"));

        assertThat(runner.runIfDue(SYNC_DATE)).isTrue();

        verify(runRepository).complete(any(UUID.class), any(ImportSummary.class), any());
        verify(runRepository, never()).fail(any(), any(), any(), any());
    }

    @Test
    void skipsWhenAnotherInstanceOwnsTheDate() {
        when(runRepository.tryStart(any(), any(), any(), any())).thenReturn(false);

        assertThat(runner.runIfDue(SYNC_DATE)).isFalse();

        verify(syncService, never()).synchronize(any(), any());
    }

    @Test
    void recordsSuccessfulRun() {
        ContentChangeSet locationChange = ContentChangeSet.of(
                "100",
                Set.of(ContentChangedField.LOCATION),
                ContentLifecycleChange.NONE,
                new TourApiContentLocation(37.4, 127.1),
                new TourApiContentLocation(37.6, 127.6));
        ImportSummary summary = new ImportSummary(
                10, 2, 0, 0, 1, 3, 1, 0, List.of(locationChange));
        when(runRepository.tryStart(any(), any(), any(), any())).thenReturn(true);
        when(syncService.synchronize(any(UUID.class), eq(SYNC_DATE))).thenReturn(summary);

        assertThat(runner.runIfDue(SYNC_DATE)).isTrue();

        verify(runRepository).complete(any(UUID.class), any(ImportSummary.class), any());
        verify(cacheSynchronizationService).applyAvailableRefreshes();
        verify(runRepository, never()).fail(any(), any(), any(), any());
    }

    @Test
    void skipsMapCacheRefreshForDetailOnlyChanges() {
        ContentChangeSet detailChange = ContentChangeSet.of(
                "100",
                Set.of(ContentChangedField.DESCRIPTION),
                ContentLifecycleChange.NONE,
                null,
                new TourApiContentLocation(37.6, 127.6));
        ImportSummary summary = new ImportSummary(
                10, 1, 0, 0, 0, 0, 0, 0, List.of(detailChange));
        when(runRepository.tryStart(any(), any(), any(), any())).thenReturn(true);
        when(syncService.synchronize(any(UUID.class), eq(SYNC_DATE))).thenReturn(summary);

        assertThat(runner.runIfDue(SYNC_DATE)).isTrue();

        verify(cacheSynchronizationService).applyAvailableRefreshes();
    }

    @Test
    void retriesRetryableFailureAndThenSucceeds() {
        ImportSummary summary = new ImportSummary(10, 2, 0, 0, 0, 3, 1, 0);
        when(runRepository.tryStart(any(), any(), any(), any())).thenReturn(true);
        when(syncService.synchronize(any(UUID.class), eq(SYNC_DATE)))
                .thenThrow(new TourApiException("temporary", true))
                .thenReturn(summary);

        assertThat(runner.runIfDue(SYNC_DATE)).isTrue();

        verify(syncService, times(2)).synchronize(any(UUID.class), eq(SYNC_DATE));
        verify(runRepository).recordAttempt(any(), org.mockito.ArgumentMatchers.eq(2), any());
    }

    @Test
    void recordsFailureWithoutRetryForPermanentError() {
        when(runRepository.tryStart(any(), any(), any(), any())).thenReturn(true);
        when(syncService.synchronize(any(UUID.class), eq(SYNC_DATE)))
                .thenThrow(new TourApiException("invalid configuration", false));

        assertThat(runner.runIfDue(SYNC_DATE)).isFalse();

        verify(syncService).synchronize(any(UUID.class), eq(SYNC_DATE));
        verify(runRepository).fail(
                any(),
                org.mockito.ArgumentMatchers.eq("SYNCHRONIZE"),
                any(TourApiException.class),
                any());
        verify(runRepository, never()).complete(any(), any(), any());
    }
}
