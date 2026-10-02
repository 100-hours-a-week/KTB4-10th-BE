package com.ktb10.kgb.tools.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class TourApiSnapshotStorageTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDirectory;

    @Test
    void savesSnapshotUnderDateAndRunIdInLocalDirectory() throws Exception {
        byte[] raw = "{\"response\":{}}".getBytes(StandardCharsets.UTF_8);
        TourApiSnapshotStorage storage = new TourApiSnapshotStorage(
                TourApiAreaSnapshotValidatorTest.properties(tempDirectory.toString()),
                () -> null,
                FIXED_CLOCK);

        String storedAt = storage.save(raw);

        Path stored = Path.of(storedAt);
        assertThat(stored).exists();
        assertThat(stored).hasContent("{\"response\":{}}");
        assertThat(stored.toString()).contains("2026-10-02");
        assertThat(stored.getFileName().toString()).isEqualTo("areaBasedList2.json");
    }

    @Test
    void savesSnapshotToS3UriFromSingleLocationProperty() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        TourApiSnapshotStorage storage = new TourApiSnapshotStorage(
                TourApiAreaSnapshotValidatorTest.properties("s3://kgb-tour/raw"),
                () -> s3Client,
                FIXED_CLOCK);

        String storedAt = storage.save("{}".getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<PutObjectRequest> request =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("kgb-tour");
        assertThat(request.getValue().key())
                .startsWith("raw/2026-10-02/")
                .endsWith("/areaBasedList2.json");
        assertThat(storedAt).isEqualTo(
                "s3://kgb-tour/" + request.getValue().key());
    }
}
