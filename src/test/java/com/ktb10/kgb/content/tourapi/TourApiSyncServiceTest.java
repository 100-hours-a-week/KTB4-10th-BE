package com.ktb10.kgb.content.tourapi;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.content.tourapi.TourApiSnapshotValidator.SnapshotType;
import com.ktb10.kgb.content.tourapi.TourApiSnapshotValidator.ValidatedSnapshot;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TourApiSyncServiceTest {

    private final TourApiClient client = mock(TourApiClient.class);
    private final TourApiSnapshotValidator validator = mock(TourApiSnapshotValidator.class);
    private final TourApiSnapshotStorage storage = mock(TourApiSnapshotStorage.class);
    private final TourApiImportService importer = mock(TourApiImportService.class);
    private TourApiSyncService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), ZoneOffset.UTC);
        service = new TourApiSyncService(
                client,
                validator,
                storage,
                importer,
                TourApiTestFixtures.properties("data/tourism/raw"),
                clock);
    }

    @Test
    void calculatesFestivalWindowAndPassesValidatedResponsesToImporter() {
        byte[] area = "area".getBytes(StandardCharsets.UTF_8);
        byte[] festival = "festival".getBytes(StandardCharsets.UTF_8);
        when(client.fetchAreaSnapshot()).thenReturn(area);
        when(client.fetchFestivalSnapshot(
                LocalDate.of(2026, 7, 11),
                LocalDate.of(2028, 10, 9))).thenReturn(festival);
        when(validator.validate(SnapshotType.AREA, area))
                .thenReturn(new ValidatedSnapshot(SnapshotType.AREA, area, 10));
        when(validator.validate(SnapshotType.FESTIVAL, festival))
                .thenReturn(new ValidatedSnapshot(SnapshotType.FESTIVAL, festival, 3));

        service.synchronize();

        verify(importer).importSnapshots(area, festival);
    }

    @Test
    void doesNotStoreOrImportWhenFestivalValidationFails() {
        byte[] area = "area".getBytes(StandardCharsets.UTF_8);
        byte[] festival = "festival".getBytes(StandardCharsets.UTF_8);
        when(client.fetchAreaSnapshot()).thenReturn(area);
        when(client.fetchFestivalSnapshot(
                LocalDate.of(2026, 7, 11),
                LocalDate.of(2028, 10, 9))).thenReturn(festival);
        when(validator.validate(SnapshotType.AREA, area))
                .thenReturn(new ValidatedSnapshot(SnapshotType.AREA, area, 10));
        when(validator.validate(SnapshotType.FESTIVAL, festival))
                .thenThrow(new TourApiException("incomplete", false));

        assertThatThrownBy(service::synchronize).isInstanceOf(TourApiException.class);
        verify(storage, never()).save(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
        verify(importer, never()).importSnapshots(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
