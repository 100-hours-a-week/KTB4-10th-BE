package com.ktb10.kgb.content.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class TourApiDetailBackfillServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TourApiDetailSyncService detailSyncService =
            mock(TourApiDetailSyncService.class);
    private final TourApiDetailBackfillService service =
            new TourApiDetailBackfillService(jdbcTemplate, detailSyncService);

    @Test
    @SuppressWarnings("unchecked")
    void processesOneCursorBasedBatchAndReturnsNextCursor() {
        TourApiDetailTarget first = new TourApiDetailTarget(10L, "100", "12", false);
        TourApiDetailTarget second = new TourApiDetailTarget(20L, "200", "15", true);
        when(jdbcTemplate.query(
                        anyString(),
                        any(RowMapper.class),
                        eq(0L),
                        eq(2)))
                .thenReturn(List.of(first, second));
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(20L)))
                .thenReturn(true);

        TourApiDetailBackfillService.BackfillBatchResult result =
                service.runBatch(0L, 2, Duration.ZERO);

        assertThat(result.afterId()).isZero();
        assertThat(result.nextAfterId()).isEqualTo(20L);
        assertThat(result.targets()).isEqualTo(2);
        assertThat(result.completed()).isEqualTo(2);
        assertThat(result.failedSourceContentIds()).isEmpty();
        assertThat(result.hasMore()).isTrue();
        verify(detailSyncService).synchronizeTarget(anyString(), eq(first));
        verify(detailSyncService).synchronizeTarget(anyString(), eq(second));
    }

    @Test
    @SuppressWarnings("unchecked")
    void continuesBatchAndReportsFailedContentIds() {
        TourApiDetailTarget first = new TourApiDetailTarget(10L, "100", "12", false);
        TourApiDetailTarget second = new TourApiDetailTarget(20L, "200", "15", true);
        when(jdbcTemplate.query(
                        anyString(),
                        any(RowMapper.class),
                        eq(0L),
                        eq(2)))
                .thenReturn(List.of(first, second));
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(20L)))
                .thenReturn(false);
        when(detailSyncService.synchronizeTarget(anyString(), eq(first)))
                .thenThrow(new TourApiException("failure", false));

        TourApiDetailBackfillService.BackfillBatchResult result =
                service.runBatch(0L, 2, Duration.ZERO);

        assertThat(result.completed()).isEqualTo(1);
        assertThat(result.failedSourceContentIds()).containsExactly("100");
        assertThat(result.hasMore()).isFalse();
        verify(detailSyncService).synchronizeTarget(anyString(), eq(second));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsCompletedResultWhenNoTargetsRemain() {
        when(jdbcTemplate.query(
                        anyString(),
                        any(RowMapper.class),
                        eq(30L),
                        eq(100)))
                .thenReturn(List.of());

        TourApiDetailBackfillService.BackfillBatchResult result =
                service.runBatch(30L, 100, Duration.ZERO);

        assertThat(result.nextAfterId()).isEqualTo(30L);
        assertThat(result.targets()).isZero();
        assertThat(result.hasMore()).isFalse();
    }
}
