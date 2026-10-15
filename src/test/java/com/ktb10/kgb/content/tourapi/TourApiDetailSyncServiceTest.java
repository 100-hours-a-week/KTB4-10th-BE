package com.ktb10.kgb.content.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class TourApiDetailSyncServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TourApiClient client = mock(TourApiClient.class);
    private final TourApiSnapshotStorage storage = mock(TourApiSnapshotStorage.class);
    private final TourApiDetailImportService importer = mock(TourApiDetailImportService.class);
    private TourApiDetailSyncService service;

    @BeforeEach
    void setUp() {
        service = new TourApiDetailSyncService(
                new ObjectMapper(),
                jdbcTemplate,
                client,
                storage,
                importer,
                TourApiTestFixtures.schedulerProperties());
    }

    @Test
    @SuppressWarnings("unchecked")
    void synchronizesOnlyPreviousDayCreatedOrModifiedTargets() {
        byte[] area = response("""
                [
                  {
                    "contentid":"100",
                    "contenttypeid":"12",
                    "lclsSystm1":"NA",
                    "createdtime":"20261013010000",
                    "modifiedtime":"20261001010000"
                  },
                  {
                    "contentid":"200",
                    "contenttypeid":"15",
                    "lclsSystm1":"EV",
                    "createdtime":"20260101010000",
                    "modifiedtime":"20261013235959"
                  },
                  {
                    "contentid":"300",
                    "contenttypeid":"12",
                    "lclsSystm1":"NA",
                    "createdtime":"20260101010000",
                    "modifiedtime":"20261012010000"
                  }
                ]
                """);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("100")))
                .thenReturn(List.of(10L));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("200")))
                .thenReturn(List.of(20L));
        when(client.fetchDetailCommon("100")).thenReturn(response("[]"));
        when(client.fetchDetailCommon("200")).thenReturn(response("[]"));
        when(client.fetchDetailIntro("200", "15")).thenReturn(response("[]"));

        TourApiDetailSyncService.DetailSyncSummary summary = service.synchronize(
                "run-1",
                LocalDate.of(2026, 10, 13),
                area);

        assertThat(summary.targets()).isEqualTo(2);
        assertThat(summary.completed()).isEqualTo(2);
        assertThat(summary.failed()).isZero();
        verify(client).fetchDetailCommon("100");
        verify(client).fetchDetailCommon("200");
        verify(client).fetchDetailIntro("200", "15");
        verify(client, never()).fetchDetailCommon("300");
        verify(importer).importDetail(
                eq(new TourApiDetailTarget(10L, "100", "12", false)),
                any(byte[].class),
                eq(null));
        verify(importer).importDetail(
                eq(new TourApiDetailTarget(20L, "200", "15", true)),
                any(byte[].class),
                any(byte[].class));
    }

    @Test
    void excludesUnsupportedClassifications() {
        byte[] area = response("""
                [
                  {
                    "contentid":"400",
                    "contenttypeid":"38",
                    "lclsSystm1":"SH",
                    "createdtime":"20261013010000",
                    "modifiedtime":"20261013010000"
                  }
                ]
                """);

        TourApiDetailSyncService.DetailSyncSummary summary = service.synchronize(
                "run-2",
                LocalDate.of(2026, 10, 13),
                area);

        assertThat(summary.targets()).isZero();
        verify(client, never()).fetchDetailCommon(anyString());
    }

    private static byte[] response(String items) {
        return ("{\"response\":{\"body\":{\"items\":{\"item\":"
                        + items
                        + "}}}}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
