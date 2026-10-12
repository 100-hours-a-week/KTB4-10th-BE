package com.ktb10.kgb.content.tourapi;

import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Supplier;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** 원본 스냅샷을 환경변수에 지정된 로컬 경로 또는 S3 URI에 저장합니다. */
public class TourApiSnapshotStorage {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ISO_LOCAL_DATE;

    private final TourApiProperties properties;
    private final Supplier<S3Client> s3ClientSupplier;
    private final Clock clock;

    public TourApiSnapshotStorage(
            TourApiProperties properties,
            Supplier<S3Client> s3ClientSupplier) {
        this(properties, s3ClientSupplier, Clock.systemUTC());
    }

    TourApiSnapshotStorage(
            TourApiProperties properties,
            Supplier<S3Client> s3ClientSupplier,
            Clock clock) {
        this.properties = properties;
        this.s3ClientSupplier = s3ClientSupplier;
        this.clock = clock;
    }

    public String save(String runId, String fileName, byte[] raw) {
        String location = properties.snapshotLocation();
        if (location == null || location.isBlank()) {
            throw new TourApiException(
                    "TOUR_API_SNAPSHOT_LOCATION이 설정되지 않았습니다.",
                    false);
        }
        String suffix = snapshotSuffix(runId, fileName);
        if (location.startsWith("s3://")) {
            return saveToS3(location, suffix, raw);
        }
        return saveToLocal(location, suffix, raw);
    }

    private String snapshotSuffix(String runId, String fileName) {
        LocalDate date = LocalDate.now(clock.withZone(SEOUL));
        return DATE_PATH.format(date) + "/" + runId + "/" + fileName;
    }

    private String saveToLocal(String location, String suffix, byte[] raw) {
        Path target = Path.of(location).resolve(suffix).normalize();
        Path parent = Objects.requireNonNull(target.getParent());
        try {
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, "areaBasedList2-", ".part");
            Files.write(temporary, raw);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target.toAbsolutePath().toString();
        } catch (IOException exception) {
            throw new TourApiException(
                    "TourAPI 로컬 스냅샷 저장에 실패했습니다.",
                    false,
                    exception);
        }
    }

    private String saveToS3(String location, String suffix, byte[] raw) {
        URI uri;
        try {
            uri = URI.create(location);
        } catch (IllegalArgumentException exception) {
            throw new TourApiException(
                    "S3 스냅샷 위치 URI가 올바르지 않습니다.",
                    false,
                    exception);
        }
        String bucket = uri.getHost();
        if (bucket == null || bucket.isBlank()) {
            throw new TourApiException(
                    "S3 스냅샷 버킷이 없습니다.",
                    false);
        }
        String prefix = uri.getPath().replaceFirst("^/", "").replaceFirst("/+$", "");
        String key = prefix.isEmpty() ? suffix : prefix + "/" + suffix;
        S3Client s3Client = s3ClientSupplier.get();
        if (s3Client == null) {
            throw new TourApiException(
                    "S3 스냅샷 저장 Client가 구성되지 않았습니다.",
                    false);
        }
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("application/json")
                            .build(),
                    RequestBody.fromBytes(raw));
            return "s3://" + bucket + "/" + key;
        } catch (RuntimeException exception) {
            throw new TourApiException(
                    "TourAPI S3 스냅샷 저장에 실패했습니다.",
                    false,
                    exception);
        }
    }
}
