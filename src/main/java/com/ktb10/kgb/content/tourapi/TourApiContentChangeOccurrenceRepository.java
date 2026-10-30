package com.ktb10.kgb.content.tourapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 동기화 데이터와 같은 트랜잭션에서 콘텐츠 변경 occurrence를 병합 저장합니다. */
@Repository
public class TourApiContentChangeOccurrenceRepository {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TourApiContentChangeOccurrenceRepository.class);
    private static final String SOURCE_PROVIDER = "TOUR_API";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TourApiContentChangeOccurrenceRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int merge(UUID runId, Collection<ContentChangeSet> changes) {
        int merged = 0;
        int inserted = 0;
        int updated = 0;
        for (ContentChangeSet change : changes) {
            if (!isMeaningful(change)) {
                continue;
            }
            if (mergeOne(runId, change)) {
                updated++;
            } else {
                inserted++;
            }
            merged++;
        }
        if (merged > 0) {
            LOGGER.info(
                    "TourAPI change occurrences persisted: runId={}, candidates={}, "
                            + "inserted={}, merged={}",
                    runId,
                    changes.size(),
                    inserted,
                    updated);
        }
        return merged;
    }

    @Transactional(readOnly = true)
    public List<TourApiContentChangeOccurrence> findAllByRunId(UUID runId) {
        return jdbcTemplate.query(
                """
                SELECT id, run_id, source_content_id, lifecycle_change,
                       changed_fields_json,
                       previous_latitude, previous_longitude,
                       current_latitude, current_longitude,
                       created_at, updated_at
                FROM tour_api_content_change_occurrences
                WHERE run_id = ? AND source_provider = ?
                ORDER BY id
                """,
                this::mapOccurrence,
                runId.toString(),
                SOURCE_PROVIDER);
    }

    private boolean mergeOne(UUID runId, ContentChangeSet change) {
        Optional<StoredOccurrence> existing = findForUpdate(runId, change.sourceContentId());
        if (existing.isEmpty()) {
            insert(runId, change);
            return false;
        }
        ContentChangeSet merged = existing.get().changeSet().merge(change);
        jdbcTemplate.update(
                """
                UPDATE tour_api_content_change_occurrences
                SET lifecycle_change = ?, changed_fields_json = ?,
                    previous_latitude = ?, previous_longitude = ?,
                    current_latitude = ?, current_longitude = ?,
                    updated_at = UTC_TIMESTAMP(6)
                WHERE id = ?
                """,
                merged.lifecycleChange().name(),
                changedFieldsJson(merged.changedFields()),
                latitude(merged.previousLocation()),
                longitude(merged.previousLocation()),
                latitude(merged.currentLocation()),
                longitude(merged.currentLocation()),
                existing.get().id());
        return true;
    }

    private Optional<StoredOccurrence> findForUpdate(UUID runId, String sourceContentId) {
        return jdbcTemplate.query(
                """
                SELECT id, lifecycle_change, changed_fields_json,
                       previous_latitude, previous_longitude,
                       current_latitude, current_longitude
                FROM tour_api_content_change_occurrences
                WHERE run_id = ? AND source_provider = ? AND source_content_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new StoredOccurrence(
                        resultSet.getLong("id"),
                        ContentChangeSet.of(
                                sourceContentId,
                                changedFields(resultSet.getString("changed_fields_json")),
                                ContentLifecycleChange.valueOf(
                                        resultSet.getString("lifecycle_change")),
                                location(resultSet, "previous_latitude", "previous_longitude"),
                                location(resultSet, "current_latitude", "current_longitude"))),
                runId.toString(),
                SOURCE_PROVIDER,
                sourceContentId).stream().findFirst();
    }

    private void insert(UUID runId, ContentChangeSet change) {
        jdbcTemplate.update(
                """
                INSERT INTO tour_api_content_change_occurrences (
                    run_id, source_provider, source_content_id,
                    lifecycle_change, changed_fields_json,
                    previous_latitude, previous_longitude,
                    current_latitude, current_longitude,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                runId.toString(),
                SOURCE_PROVIDER,
                change.sourceContentId(),
                change.lifecycleChange().name(),
                changedFieldsJson(change.changedFields()),
                latitude(change.previousLocation()),
                longitude(change.previousLocation()),
                latitude(change.currentLocation()),
                longitude(change.currentLocation()));
    }

    private TourApiContentChangeOccurrence mapOccurrence(
            ResultSet resultSet,
            int rowNumber) throws SQLException {
        return new TourApiContentChangeOccurrence(
                resultSet.getLong("id"),
                UUID.fromString(resultSet.getString("run_id")),
                resultSet.getString("source_content_id"),
                changedFields(resultSet.getString("changed_fields_json")),
                ContentLifecycleChange.valueOf(resultSet.getString("lifecycle_change")),
                location(resultSet, "previous_latitude", "previous_longitude"),
                location(resultSet, "current_latitude", "current_longitude"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("updated_at").toLocalDateTime());
    }

    private String changedFieldsJson(Set<ContentChangedField> fields) {
        List<String> names = fields.stream()
                .map(Enum::name)
                .sorted()
                .toList();
        try {
            return objectMapper.writeValueAsString(names);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("TourAPI 변경 필드를 JSON으로 변환할 수 없습니다.", exception);
        }
    }

    private Set<ContentChangedField> changedFields(String json) {
        try {
            List<String> names = objectMapper.readValue(json, STRING_LIST);
            if (names.isEmpty()) {
                return Set.of();
            }
            EnumSet<ContentChangedField> fields = EnumSet.noneOf(ContentChangedField.class);
            names.stream().map(ContentChangedField::valueOf).forEach(fields::add);
            return fields;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("저장된 TourAPI 변경 필드 JSON을 읽을 수 없습니다.", exception);
        }
    }

    private static boolean isMeaningful(ContentChangeSet change) {
        return !change.changedFields().isEmpty()
                || change.lifecycleChange() != ContentLifecycleChange.NONE;
    }

    private static BigDecimal latitude(TourApiContentLocation location) {
        return location == null ? null : BigDecimal.valueOf(location.latitude());
    }

    private static BigDecimal longitude(TourApiContentLocation location) {
        return location == null ? null : BigDecimal.valueOf(location.longitude());
    }

    private static TourApiContentLocation location(
            ResultSet resultSet,
            String latitudeColumn,
            String longitudeColumn) throws SQLException {
        BigDecimal latitude = resultSet.getBigDecimal(latitudeColumn);
        BigDecimal longitude = resultSet.getBigDecimal(longitudeColumn);
        if (latitude == null && longitude == null) {
            return null;
        }
        if (latitude == null || longitude == null) {
            throw new IllegalStateException("저장된 TourAPI 변경 위치 좌표가 올바르지 않습니다.");
        }
        return new TourApiContentLocation(latitude.doubleValue(), longitude.doubleValue());
    }

    private record StoredOccurrence(Long id, ContentChangeSet changeSet) {
    }
}
