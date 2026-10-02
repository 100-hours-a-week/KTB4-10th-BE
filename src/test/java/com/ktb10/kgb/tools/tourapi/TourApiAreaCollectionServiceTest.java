package com.ktb10.kgb.tools.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.tools.tourapi.TourApiAreaSnapshotValidator.ValidatedAreaSnapshot;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TourApiAreaCollectionServiceTest {

    private final TourApiAreaClient client = mock(TourApiAreaClient.class);
    private final TourApiAreaSnapshotValidator validator =
            mock(TourApiAreaSnapshotValidator.class);
    private final TourApiSnapshotStorage storage = mock(TourApiSnapshotStorage.class);
    private final TourApiInitialImportService importer =
            mock(TourApiInitialImportService.class);

    private TourApiAreaCollectionService service;

    @BeforeEach
    void setUp() {
        service = new TourApiAreaCollectionService(
                client,
                validator,
                storage,
                importer,
                TourApiAreaSnapshotValidatorTest.properties("data/tourism/raw"));
    }

    @Test
    void retriesFetchThenStoresAndImportsValidatedSnapshot() {
        byte[] raw = "{}".getBytes(StandardCharsets.UTF_8);
        when(client.fetchSnapshot())
                .thenThrow(new TourApiCollectionException("temporary", true))
                .thenReturn(raw);
        when(validator.validate(raw)).thenReturn(new ValidatedAreaSnapshot(raw, 1, 60000));
        when(storage.save(raw)).thenReturn("/tmp/snapshot.json");
        ImportSummary summary = new ImportSummary(1, 1, 0, 0, 0, 0, 0);
        when(importer.importAreaSnapshot(raw)).thenReturn(summary);

        TourApiAreaCollectionResult result = service.collectAndImport();

        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.importSummary()).isEqualTo(summary);
        verify(storage).save(raw);
        verify(importer).importAreaSnapshot(raw);
    }

    @Test
    void doesNotRetryOrPersistNonRetryableFailure() {
        when(client.fetchSnapshot())
                .thenThrow(new TourApiCollectionException("bad key", false));

        assertThatThrownBy(service::collectAndImport)
                .isInstanceOf(TourApiCollectionException.class)
                .hasMessageContaining("bad key");
        verify(storage, never()).save(org.mockito.ArgumentMatchers.any());
        verify(importer, never()).importAreaSnapshot(org.mockito.ArgumentMatchers.any());
    }
}
