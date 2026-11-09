package com.ktb10.kgb.content.tourapi;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.guidebook.entity.AdministrativeDistrict;
import com.ktb10.kgb.guidebook.entity.AdministrativeProvince;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 검증된 TourAPI 목록 JSON을 서비스 테이블에 증분 반영합니다. */
@Service
public class TourApiImportService {

    private static final String SOURCE_PROVIDER = "TOUR_API";
    private static final DateTimeFormatter SOURCE_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter SOURCE_DATE =
            DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> DETAIL_ALLOWED_CLASSIFICATIONS =
            Set.of("NA", "HS", "VE", "EX", "LS", "EV");

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public TourApiImportService(
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 검증된 areaBasedList2와 searchFestival2 응답을 한 트랜잭션으로 증분 반영합니다. */
    @Transactional
    public ImportSummary importSnapshots(
            byte[] areaSnapshot,
            byte[] festivalSnapshot,
            LocalDate syncDate) {
        List<JsonNode> events = readItems(festivalSnapshot, "수집한 searchFestival2 스냅샷");
        Map<String, ExistingEvent> existingEvents = loadExistingEvents();
        Set<String> expiredEventIds = resolveExpiredEventIds(
                events,
                existingEvents,
                syncDate);
        ContentImportResult contentResult = synchronizeAndImportContents(
                areaSnapshot,
                expiredEventIds);
        EventImportResult eventResult = importEvents(
                events,
                contentResult.contentIds(),
                existingEvents,
                expiredEventIds);
        List<ContentChangeSet> changeSets = mergeChangeSets(
                contentResult.changeSets(),
                eventResult.changeSets());
        return new ImportSummary(
                contentResult.sourceContents(),
                contentResult.imported(),
                contentResult.missingCoordinates(),
                contentResult.missingRegionMapping(),
                contentResult.inactive(),
                events.size(),
                eventResult.imported(),
                eventResult.invalid(),
                changeSets);
    }

    private ContentImportResult synchronizeAndImportContents(
            byte[] areaSnapshot,
            Set<String> expiredEventIds) {
        RegionMetadata regionMetadata = extractRegionMetadata(areaSnapshot);
        RegionMappings regionMappings = synchronizeRegions(
                regionMetadata.regionNames(),
                regionMetadata.provinceCodes());
        return importContents(areaSnapshot, regionMappings, expiredEventIds);
    }

    private List<JsonNode> readItems(byte[] snapshot, String sourceName) {
        try {
            JsonNode items = objectMapper.readTree(snapshot)
                    .path("response")
                    .path("body")
                    .path("items")
                    .path("item");
            if (!items.isArray()) {
                throw new IllegalArgumentException(
                        "TourAPI item 배열을 찾을 수 없습니다: " + sourceName);
            }
            List<JsonNode> result = new ArrayList<>();
            items.forEach(result::add);
            return result;
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "TourAPI JSON을 읽을 수 없습니다: " + sourceName,
                    exception);
        }
    }

    private RegionMetadata extractRegionMetadata(byte[] areaSnapshot) {
        Map<SourceRegionKey, Map<ServiceRegionKey, Integer>> frequencies = new HashMap<>();
        Map<AdministrativeProvince, String> provinceCodes = new HashMap<>();
        forEachItem(areaSnapshot, "수집한 areaBasedList2 스냅샷", content -> {
            SourceRegionKey sourceKey = sourceRegionKey(content);
            ServiceRegionKey serviceKey = serviceRegionKey(content.path("addr1").asText());
            if (sourceKey != null && serviceKey != null) {
                frequencies.computeIfAbsent(sourceKey, ignored -> new HashMap<>())
                        .merge(serviceKey, 1, Integer::sum);
            }
            String sourceCode = text(content, "lDongRegnCd");
            String address = text(content, "addr1");
            if (sourceCode == null || address == null) {
                return;
            }
            String[] tokens = address.split("\\s+");
            AdministrativeProvince province = provinceOrNull(tokens[0]);
            if (province != null) {
                provinceCodes.putIfAbsent(province, sourceCode);
            }
        });

        Map<SourceRegionKey, ServiceRegionKey> regionNames = new HashMap<>();
        frequencies.forEach((source, candidates) -> candidates.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .ifPresent(entry -> regionNames.put(source, entry.getKey())));
        return new RegionMetadata(regionNames, provinceCodes);
    }

    private ServiceRegionKey serviceRegionKey(String address) {
        if (!StringUtils.hasText(address)) {
            return null;
        }
        String[] tokens = address.trim().split("\\s+");
        if (tokens.length < 2) {
            return null;
        }
        try {
            AdministrativeProvince province =
                    AdministrativeProvince.fromDisplayName(tokens[0]);
            if (province == AdministrativeProvince.SEJONG) {
                return new ServiceRegionKey(province, province.displayName());
            }
            AdministrativeDistrict.fromDisplayName(province, tokens[1]);
            return new ServiceRegionKey(province, tokens[1]);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private AdministrativeProvince provinceOrNull(String displayName) {
        try {
            return AdministrativeProvince.fromDisplayName(displayName);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private RegionMappings synchronizeRegions(
            Map<SourceRegionKey, ServiceRegionKey> regionNames,
            Map<AdministrativeProvince, String> provinceCodes) {
        provinceCodes.forEach(this::upsertProvince);
        Map<AdministrativeProvince, Long> provinceIds = loadProvinceIds(provinceCodes);
        for (AdministrativeDistrict district : AdministrativeDistrict.values()) {
            Long provinceId = provinceIds.get(district.province());
            if (provinceId != null) {
                upsertDistrict(district, provinceId, provinceCodes.get(district.province()));
            }
        }

        Map<ServiceRegionKey, Long> districtIds = loadDistrictIds(provinceIds);
        Map<SourceRegionKey, Long> mappings = new HashMap<>();
        regionNames.forEach((source, service) -> {
            Long regionId = districtIds.get(service);
            if (regionId != null) {
                upsertSourceRegionMapping(source, regionId);
                mappings.put(source, regionId);
            }
        });
        return new RegionMappings(mappings, districtIds);
    }

    private void upsertProvince(
            AdministrativeProvince province,
            String administrativeCode) {
        jdbcTemplate.update(
                """
                INSERT INTO regions (
                    parent_id, administrative_code, name, region_level
                ) VALUES (NULL, ?, ?, 'PROVINCE')
                ON DUPLICATE KEY UPDATE
                    parent_id = NULL,
                    name = VALUES(name),
                    region_level = 'PROVINCE'
                """,
                administrativeCode,
                province.displayName());
    }

    private Map<AdministrativeProvince, Long> loadProvinceIds(
            Map<AdministrativeProvince, String> provinceCodes) {
        Map<AdministrativeProvince, Long> result = new HashMap<>();
        provinceCodes.forEach((province, code) -> {
            Long id = jdbcTemplate.queryForObject(
                    "SELECT id FROM regions WHERE administrative_code = ?",
                    Long.class,
                    code);
            result.put(province, id);
        });
        return result;
    }

    private void upsertDistrict(
            AdministrativeDistrict district,
            Long provinceId,
            String provinceCode) {
        String administrativeCode = district.name().substring("REGION_".length());
        if (administrativeCode.equals(provinceCode)) {
            administrativeCode += "-D";
        }
        jdbcTemplate.update(
                """
                INSERT INTO regions (
                    parent_id, administrative_code, name, region_level
                ) VALUES (?, ?, ?, 'DISTRICT')
                ON DUPLICATE KEY UPDATE
                    parent_id = VALUES(parent_id),
                    name = VALUES(name),
                    region_level = 'DISTRICT'
                """,
                provinceId,
                administrativeCode,
                district.displayName());
    }

    private Map<ServiceRegionKey, Long> loadDistrictIds(
            Map<AdministrativeProvince, Long> provinceIds) {
        Map<ServiceRegionKey, Long> result = new HashMap<>();
        provinceIds.forEach((province, provinceId) -> jdbcTemplate.query(
                """
                        SELECT id, name
                        FROM regions
                        WHERE parent_id = ? AND region_level = 'DISTRICT'
                """,
                (resultSet, rowNumber) -> Map.entry(
                        new ServiceRegionKey(province, resultSet.getString("name")),
                        resultSet.getLong("id")),
                provinceId).forEach(entry -> result.put(entry.getKey(), entry.getValue())));
        return result;
    }

    private void upsertSourceRegionMapping(SourceRegionKey source, Long regionId) {
        jdbcTemplate.update(
                """
                INSERT INTO tour_api_region_mappings (
                    source_provider, source_region_code, source_district_code,
                    region_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE
                    region_id = VALUES(region_id),
                    updated_at = NOW(6)
                """,
                SOURCE_PROVIDER,
                source.regionCode(),
                source.districtCode(),
                regionId);
    }

    private ContentImportResult importContents(
            byte[] areaSnapshot,
            RegionMappings regionMappings,
            Set<String> expiredEventIds) {
        Map<String, ExistingVersion> existingVersions = loadExistingVersions();
        List<ContentRow> changedRows = new ArrayList<>();
        Set<String> contentIds = new HashSet<>();
        Set<String> sourceContentIds = new HashSet<>();
        int[] sourceContents = {0};
        int[] missingCoordinates = {0};
        int[] missingRegionMapping = {0};

        forEachItem(areaSnapshot, "수집한 areaBasedList2 스냅샷", source -> {
            sourceContents[0]++;
            String sourceContentId = text(source, "contentid");
            if (sourceContentId != null && expiredEventIds.contains(sourceContentId)) {
                return;
            }
            if (sourceContentId != null) {
                sourceContentIds.add(sourceContentId);
            }
            BigDecimal longitude = decimal(source, "mapx");
            BigDecimal latitude = decimal(source, "mapy");
            if (longitude == null || latitude == null) {
                missingCoordinates[0]++;
                return;
            }
            SourceRegionKey sourceRegion = sourceRegionKey(source);
            Long regionId = regionMappings.bySource().get(sourceRegion);
            if (regionId == null) {
                ServiceRegionKey serviceRegion =
                        serviceRegionKey(source.path("addr1").asText());
                regionId = regionMappings.byService().get(serviceRegion);
            }
            if (regionId == null) {
                missingRegionMapping[0]++;
                return;
            }
            String title = text(source, "title");
            String classificationCode1 = text(source, "lclsSystm1");
            if (sourceContentId == null || title == null || classificationCode1 == null) {
                return;
            }
            contentIds.add(sourceContentId);
            ExistingVersion existing = existingVersions.get(sourceContentId);
            LocalDateTime sourceModifiedAt = dateTime(source, "modifiedtime");
            if (!needsUpsert(existing, sourceModifiedAt)) {
                return;
            }
            ContentRow row = ContentRow.from(source, regionId, longitude, latitude);
            if (row != null) {
                changedRows.add(row);
            }
        });

        List<String> missingContentIds = existingVersions.values().stream()
                .filter(existing -> "ACTIVE".equals(existing.status()))
                .map(ExistingVersion::sourceContentId)
                .filter(existingId -> !sourceContentIds.contains(existingId))
                .toList();
        Set<String> detailedIds = new HashSet<>(missingContentIds);
        changedRows.stream().map(ContentRow::sourceContentId).forEach(detailedIds::add);
        final Map<String, ExistingContent> existingContents = loadExistingContents(detailedIds);
        batchUpsertContents(changedRows);
        Map<String, Long> databaseContentIds = new HashMap<>();
        existingVersions.forEach((sourceContentId, existing) ->
                databaseContentIds.put(sourceContentId, existing.id()));
        Set<String> newContentIds = changedRows.stream()
                .map(ContentRow::sourceContentId)
                .filter(sourceContentId -> !databaseContentIds.containsKey(sourceContentId))
                .collect(java.util.stream.Collectors.toSet());
        databaseContentIds.putAll(loadContentDatabaseIds(newContentIds));
        List<ContentChangeSet> changedContentSets = changedRows.stream()
                .map(row -> withDetailTarget(
                        row.changeSet(existingContents.get(row.sourceContentId())),
                        row,
                        databaseContentIds.get(row.sourceContentId())))
                .toList();
        List<ContentChangeSet> inactiveChangeSets = missingContentIds.stream()
                .map(existingContents::get)
                .map(ExistingContent::inactivatedChangeSet)
                .toList();
        batchDeactivateContents(missingContentIds);
        List<ContentChangeSet> changeSets = new ArrayList<>(changedContentSets);
        changeSets.addAll(inactiveChangeSets);
        return new ContentImportResult(
                sourceContents[0],
                changedRows.size(),
                missingCoordinates[0],
                missingRegionMapping[0],
                missingContentIds.size(),
                contentIds,
                changeSets);
    }

    private Map<String, ExistingVersion> loadExistingVersions() {
        Map<String, ExistingVersion> result = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT id, source_content_id, source_modified_at, status, deleted_at
                FROM tourism_contents
                WHERE source_provider = ?
                """,
                (resultSet, rowNumber) -> new ExistingVersion(
                        resultSet.getLong("id"),
                        resultSet.getString("source_content_id"),
                        resultSet.getTimestamp("source_modified_at") == null
                                ? null
                                : resultSet.getTimestamp("source_modified_at").toLocalDateTime(),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("deleted_at") != null),
                SOURCE_PROVIDER).forEach(version -> result.put(version.sourceContentId(), version));
        return result;
    }

    private ContentChangeSet withDetailTarget(
            ContentChangeSet changeSet,
            ContentRow row,
            Long databaseContentId) {
        if (databaseContentId == null
                || !DETAIL_ALLOWED_CLASSIFICATIONS.contains(row.classificationCode1())) {
            return changeSet;
        }
        return changeSet.withDetailTarget(new TourApiDetailTarget(
                databaseContentId,
                row.sourceContentId(),
                row.sourceContentTypeId(),
                "EV".equals(row.classificationCode1())));
    }

    private Map<String, Long> loadContentDatabaseIds(Set<String> sourceContentIds) {
        Map<String, Long> result = new HashMap<>();
        List<String> ids = List.copyOf(sourceContentIds);
        for (int start = 0; start < ids.size(); start += 1_000) {
            List<String> chunk = ids.subList(start, Math.min(start + 1_000, ids.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(chunk.size(), "?"));
            Object[] parameters = new Object[chunk.size() + 1];
            parameters[0] = SOURCE_PROVIDER;
            for (int index = 0; index < chunk.size(); index++) {
                parameters[index + 1] = chunk.get(index);
            }
            jdbcTemplate.query(
                    """
                    SELECT id, source_content_id
                    FROM tourism_contents
                    WHERE source_provider = ? AND source_content_id IN (%s)
                    """.formatted(placeholders),
                    (org.springframework.jdbc.core.RowCallbackHandler) resultSet ->
                            result.put(
                                    resultSet.getString("source_content_id"),
                                    resultSet.getLong("id")),
                    parameters);
        }
        return result;
    }

    private boolean needsUpsert(ExistingVersion existing, LocalDateTime sourceModifiedAt) {
        return existing == null
                || !"ACTIVE".equals(existing.status())
                || existing.deleted()
                || sourceModifiedAt == null
                || !Objects.equals(sourceModifiedAt, existing.sourceModifiedAt());
    }

    private Map<String, ExistingContent> loadExistingContents(Set<String> sourceContentIds) {
        Map<String, ExistingContent> result = new HashMap<>();
        List<String> ids = List.copyOf(sourceContentIds);
        for (int start = 0; start < ids.size(); start += 1_000) {
            List<String> chunk = ids.subList(start, Math.min(start + 1_000, ids.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(chunk.size(), "?"));
            Object[] parameters = new Object[chunk.size() + 1];
            parameters[0] = SOURCE_PROVIDER;
            for (int index = 0; index < chunk.size(); index++) {
                parameters[index + 1] = chunk.get(index);
            }
            jdbcTemplate.query(
                """
                SELECT source_content_id, source_content_type_id, title, region_id,
                       source_region_code, source_district_code,
                       classification_code_1, classification_code_2,
                       classification_code_3, address,
                       ST_Longitude(location) AS longitude,
                       ST_Latitude(location) AS latitude,
                       phone, thumbnail_url, source_modified_at, status, deleted_at
                FROM tourism_contents
                WHERE source_provider = ? AND source_content_id IN (%s)
                """.formatted(placeholders),
                (resultSet, rowNumber) -> new ExistingContent(
                        resultSet.getString("source_content_id"),
                        resultSet.getString("source_content_type_id"),
                        resultSet.getString("title"),
                        resultSet.getLong("region_id"),
                        resultSet.getString("source_region_code"),
                        resultSet.getString("source_district_code"),
                        resultSet.getString("classification_code_1"),
                        resultSet.getString("classification_code_2"),
                        resultSet.getString("classification_code_3"),
                        resultSet.getString("address"),
                        resultSet.getBigDecimal("longitude"),
                        resultSet.getBigDecimal("latitude"),
                        resultSet.getString("phone"),
                        resultSet.getString("thumbnail_url"),
                        resultSet.getTimestamp("source_modified_at") == null
                                ? null
                                : resultSet.getTimestamp("source_modified_at").toLocalDateTime(),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("deleted_at") != null),
                parameters).forEach(content -> result.put(content.sourceContentId(), content));
        }
        return result;
    }

    private void forEachItem(byte[] snapshot, String sourceName, Consumer<JsonNode> consumer) {
        try (JsonParser parser = objectMapper.getFactory().createParser(snapshot)) {
            boolean found = false;
            while (parser.nextToken() != null) {
                if (parser.currentToken() != JsonToken.FIELD_NAME
                        || !"item".equals(parser.currentName())) {
                    continue;
                }
                if (parser.nextToken() != JsonToken.START_ARRAY) {
                    throw new IllegalArgumentException(
                            "TourAPI item 배열을 찾을 수 없습니다: " + sourceName);
                }
                found = true;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    consumer.accept(objectMapper.readTree(parser));
                }
                break;
            }
            if (!found) {
                throw new IllegalArgumentException(
                        "TourAPI item 배열을 찾을 수 없습니다: " + sourceName);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "TourAPI JSON을 읽을 수 없습니다: " + sourceName,
                    exception);
        }
    }

    private void batchUpsertContents(List<ContentRow> rows) {
        String sql =
                """
                INSERT INTO tourism_contents (
                    source_provider, source_content_id,
                    source_content_type_id, title, description, region_id,
                    source_region_code, source_district_code,
                    classification_code_1, classification_code_2,
                    classification_code_3, address, location, phone,
                    homepage_url, thumbnail_url, source_created_at,
                    source_modified_at, status, deleted_at, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?,
                    ST_SRID(POINT(?, ?), 4326), ?, NULL, ?, ?, ?,
                    'ACTIVE', NULL, NOW(6), NOW(6)
                )
                ON DUPLICATE KEY UPDATE
                    source_content_type_id = VALUES(source_content_type_id),
                    title = VALUES(title),
                    region_id = VALUES(region_id),
                    source_region_code = VALUES(source_region_code),
                    source_district_code = VALUES(source_district_code),
                    classification_code_1 = VALUES(classification_code_1),
                    classification_code_2 = VALUES(classification_code_2),
                    classification_code_3 = VALUES(classification_code_3),
                    address = VALUES(address),
                    location = VALUES(location),
                    phone = VALUES(phone),
                    thumbnail_url = VALUES(thumbnail_url),
                    source_created_at = VALUES(source_created_at),
                    source_modified_at = VALUES(source_modified_at),
                    status = 'ACTIVE',
                    deleted_at = NULL,
                    updated_at = NOW(6)
                """;
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index)
                    throws SQLException {
                rows.get(index).setValues(statement);
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    private void batchDeactivateContents(List<String> sourceContentIds) {
        jdbcTemplate.batchUpdate(
                """
                UPDATE tourism_contents
                SET status = 'INACTIVE', deleted_at = NOW(6), updated_at = NOW(6)
                WHERE source_provider = ? AND source_content_id = ? AND status = 'ACTIVE'
                """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index)
                            throws SQLException {
                        statement.setString(1, SOURCE_PROVIDER);
                        statement.setString(2, sourceContentIds.get(index));
                    }

                    @Override
                    public int getBatchSize() {
                        return sourceContentIds.size();
                    }
                });
    }

    private EventImportResult importEvents(
            List<JsonNode> sourceEvents,
            Set<String> importedContentIds,
            Map<String, ExistingEvent> existingEvents,
            Set<String> expiredEventIds) {
        List<EventRow> events = new ArrayList<>();
        int invalid = 0;
        for (JsonNode source : sourceEvents) {
            String contentId = text(source, "contentid");
            if (contentId != null && expiredEventIds.contains(contentId)) {
                continue;
            }
            LocalDate startDate = date(source, "eventstartdate");
            LocalDate endDate = date(source, "eventenddate");
            if (!importedContentIds.contains(contentId)
                    || startDate == null
                    || endDate == null
                    || startDate.isAfter(endDate)) {
                invalid++;
                continue;
            }
            events.add(new EventRow(contentId, startDate, endDate));
        }

        List<EventRow> changedEvents = events.stream()
                .filter(event -> !event.matches(existingEvents.get(event.sourceContentId())))
                .toList();
        List<ContentChangeSet> eventChangeSets = changedEvents.stream()
                .map(event -> event.changeSet(existingEvents.get(event.sourceContentId())))
                .toList();

        String sql =
                """
                INSERT INTO event_details (
                    content_id, start_date, end_date,
                    operating_hours, organizer, updated_at
                )
                SELECT id, ?, ?, NULL, NULL, NOW(6)
                FROM tourism_contents
                WHERE source_provider = ? AND source_content_id = ?
                ON DUPLICATE KEY UPDATE
                    start_date = VALUES(start_date),
                    end_date = VALUES(end_date),
                    updated_at = NOW(6)
                """;
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index)
                    throws SQLException {
                EventRow event = changedEvents.get(index);
                statement.setObject(1, event.startDate());
                statement.setObject(2, event.endDate());
                statement.setString(3, SOURCE_PROVIDER);
                statement.setString(4, event.sourceContentId());
            }

            @Override
            public int getBatchSize() {
                return changedEvents.size();
            }
        });
        return new EventImportResult(changedEvents.size(), invalid, eventChangeSets);
    }

    private Set<String> resolveExpiredEventIds(
            List<JsonNode> sourceEvents,
            Map<String, ExistingEvent> existingEvents,
            LocalDate syncDate) {
        Set<String> expired = existingEvents.values().stream()
                .filter(event -> event.endDate().isBefore(syncDate))
                .map(ExistingEvent::sourceContentId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        for (JsonNode source : sourceEvents) {
            String contentId = text(source, "contentid");
            LocalDate startDate = date(source, "eventstartdate");
            LocalDate endDate = date(source, "eventenddate");
            if (contentId == null
                    || startDate == null
                    || endDate == null
                    || startDate.isAfter(endDate)) {
                continue;
            }
            if (endDate.isBefore(syncDate)) {
                expired.add(contentId);
            } else {
                expired.remove(contentId);
            }
        }
        return Set.copyOf(expired);
    }

    private Map<String, ExistingEvent> loadExistingEvents() {
        Map<String, ExistingEvent> result = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT content.source_content_id, event.start_date, event.end_date
                     , ST_Longitude(content.location) AS longitude
                     , ST_Latitude(content.location) AS latitude
                FROM event_details event
                JOIN tourism_contents content ON content.id = event.content_id
                WHERE content.source_provider = ?
                """,
                (resultSet, rowNumber) -> new ExistingEvent(
                        resultSet.getString("source_content_id"),
                        resultSet.getDate("start_date").toLocalDate(),
                        resultSet.getDate("end_date").toLocalDate(),
                        resultSet.getBigDecimal("longitude"),
                        resultSet.getBigDecimal("latitude")),
                SOURCE_PROVIDER).forEach(event -> result.put(event.sourceContentId(), event));
        return result;
    }

    private SourceRegionKey sourceRegionKey(JsonNode source) {
        String regionCode = text(source, "lDongRegnCd");
        String districtCode = text(source, "lDongSignguCd");
        if (!StringUtils.hasText(regionCode) || !StringUtils.hasText(districtCode)) {
            return null;
        }
        return new SourceRegionKey(regionCode, districtCode);
    }

    private static String text(JsonNode source, String field) {
        String value = source.path(field).asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static BigDecimal decimal(JsonNode source, String field) {
        String value = text(source, field);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static LocalDateTime dateTime(JsonNode source, String field) {
        String value = text(source, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, SOURCE_DATE_TIME);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static LocalDate date(JsonNode source, String field) {
        String value = text(source, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, SOURCE_DATE);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @SafeVarargs
    private static List<ContentChangeSet> mergeChangeSets(
            List<ContentChangeSet>... changeSets) {
        Map<String, ContentChangeSet> merged = new LinkedHashMap<>();
        for (List<ContentChangeSet> changes : changeSets) {
            changes.forEach(change -> merged.merge(
                    change.sourceContentId(),
                    change,
                    ContentChangeSet::merge));
        }
        return List.copyOf(merged.values());
    }

    private record SourceRegionKey(String regionCode, String districtCode) {
    }

    private record ServiceRegionKey(
            AdministrativeProvince province,
            String districtName) {
    }

    private record RegionMappings(
            Map<SourceRegionKey, Long> bySource,
            Map<ServiceRegionKey, Long> byService) {
    }

    private record RegionMetadata(
            Map<SourceRegionKey, ServiceRegionKey> regionNames,
            Map<AdministrativeProvince, String> provinceCodes) {
    }

    private record ContentImportResult(
            int sourceContents,
            int imported,
            int missingCoordinates,
            int missingRegionMapping,
            int inactive,
            Set<String> contentIds,
            List<ContentChangeSet> changeSets) {
    }

    private record EventImportResult(
            int imported,
            int invalid,
            List<ContentChangeSet> changeSets) {
    }

    private record EventRow(
            String sourceContentId,
            LocalDate startDate,
            LocalDate endDate) {

        private boolean matches(ExistingEvent existing) {
            return existing != null
                    && startDate.equals(existing.startDate())
                    && endDate.equals(existing.endDate());
        }

        private ContentChangeSet changeSet(ExistingEvent existing) {
            EnumSet<ContentChangedField> fields = EnumSet.noneOf(ContentChangedField.class);
            if (existing == null || !startDate.equals(existing.startDate())) {
                fields.add(ContentChangedField.START_DATE);
            }
            if (existing == null || !endDate.equals(existing.endDate())) {
                fields.add(ContentChangedField.END_DATE);
            }
            return ContentChangeSet.of(
                    sourceContentId,
                    fields,
                    ContentLifecycleChange.NONE,
                    null,
                    existing == null ? null : existing.location());
        }
    }

    private record ExistingEvent(
            String sourceContentId,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal longitude,
            BigDecimal latitude) {

        private TourApiContentLocation location() {
            return longitude == null || latitude == null
                    ? null
                    : new TourApiContentLocation(latitude.doubleValue(), longitude.doubleValue());
        }
    }

    private record ExistingContent(
            String sourceContentId,
            String sourceContentTypeId,
            String title,
            long regionId,
            String sourceRegionCode,
            String sourceDistrictCode,
            String classificationCode1,
            String classificationCode2,
            String classificationCode3,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            String phone,
            String thumbnailUrl,
            LocalDateTime sourceModifiedAt,
            String status,
            boolean deleted) {

        private TourApiContentLocation location() {
            return longitude == null || latitude == null
                    ? null
                    : new TourApiContentLocation(latitude.doubleValue(), longitude.doubleValue());
        }

        private ContentChangeSet inactivatedChangeSet() {
            return ContentChangeSet.of(
                    sourceContentId,
                    EnumSet.of(ContentChangedField.STATUS),
                    ContentLifecycleChange.INACTIVATED,
                    location(),
                    null);
        }
    }

    private record ExistingVersion(
            long id,
            String sourceContentId,
            LocalDateTime sourceModifiedAt,
            String status,
            boolean deleted) {
    }

    private record ContentRow(
            String sourceContentId,
            String sourceContentTypeId,
            String title,
            long regionId,
            String sourceRegionCode,
            String sourceDistrictCode,
            String classificationCode1,
            String classificationCode2,
            String classificationCode3,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            String phone,
            String thumbnailUrl,
            LocalDateTime sourceCreatedAt,
            LocalDateTime sourceModifiedAt) {

        private static ContentRow from(
                JsonNode source,
                long regionId,
                BigDecimal longitude,
                BigDecimal latitude) {
            String sourceContentId = text(source, "contentid");
            String title = text(source, "title");
            String classificationCode1 = text(source, "lclsSystm1");
            if (sourceContentId == null || title == null || classificationCode1 == null) {
                return null;
            }
            String address = String.join(" ",
                    Objects.requireNonNullElse(text(source, "addr1"), ""),
                    Objects.requireNonNullElse(text(source, "addr2"), "")).trim();
            String thumbnail = text(source, "firstimage");
            if (thumbnail == null) {
                thumbnail = text(source, "firstimage2");
            }
            return new ContentRow(
                    limit(sourceContentId, 100),
                    limit(text(source, "contenttypeid"), 20),
                    limit(title, 200),
                    regionId,
                    limit(text(source, "lDongRegnCd"), 20),
                    limit(text(source, "lDongSignguCd"), 20),
                    limit(classificationCode1, 20),
                    limit(text(source, "lclsSystm2"), 20),
                    limit(text(source, "lclsSystm3"), 20),
                    limit(StringUtils.hasText(address) ? address : null, 500),
                    longitude,
                    latitude,
                    limit(text(source, "tel"), 50),
                    limit(thumbnail, 2048),
                    dateTime(source, "createdtime"),
                    dateTime(source, "modifiedtime"));
        }

        private ContentChangeSet changeSet(ExistingContent existing) {
            final ContentLifecycleChange lifecycleChange = lifecycleChange(existing);
            EnumSet<ContentChangedField> fields = EnumSet.noneOf(ContentChangedField.class);
            if (existing == null || !Objects.equals(title, existing.title())) {
                fields.add(ContentChangedField.TITLE);
            }
            if (existing == null || !Objects.equals(address, existing.address())) {
                fields.add(ContentChangedField.ADDRESS);
            }
            if (existing == null || !Objects.equals(phone, existing.phone())) {
                fields.add(ContentChangedField.PHONE);
            }
            if (existing == null || !Objects.equals(thumbnailUrl, existing.thumbnailUrl())) {
                fields.add(ContentChangedField.THUMBNAIL_URL);
            }
            if (existing == null || regionId != existing.regionId()) {
                fields.add(ContentChangedField.REGION);
            }
            if (existing == null
                    || !Objects.equals(sourceRegionCode, existing.sourceRegionCode())) {
                fields.add(ContentChangedField.SOURCE_REGION_CODE);
            }
            if (existing == null
                    || !Objects.equals(sourceDistrictCode, existing.sourceDistrictCode())) {
                fields.add(ContentChangedField.SOURCE_DISTRICT_CODE);
            }
            if (existing == null || locationChanged(existing)) {
                fields.add(ContentChangedField.LOCATION);
            }
            addClassificationChanges(fields, existing);
            if (lifecycleChange != ContentLifecycleChange.NONE || isInactive(existing)) {
                fields.add(ContentChangedField.STATUS);
            }
            return ContentChangeSet.of(
                    sourceContentId,
                    fields,
                    lifecycleChange,
                    existing == null ? null : existing.location(),
                    location());
        }

        private void addClassificationChanges(
                Set<ContentChangedField> fields,
                ExistingContent existing) {
            if (existing == null
                    || !Objects.equals(sourceContentTypeId, existing.sourceContentTypeId())) {
                fields.add(ContentChangedField.SOURCE_CONTENT_TYPE_ID);
            }
            if (existing == null
                    || !Objects.equals(classificationCode1, existing.classificationCode1())) {
                fields.add(ContentChangedField.CLASSIFICATION_CODE_1);
            }
            if (existing == null
                    || !Objects.equals(classificationCode2, existing.classificationCode2())) {
                fields.add(ContentChangedField.CLASSIFICATION_CODE_2);
            }
            if (existing == null
                    || !Objects.equals(classificationCode3, existing.classificationCode3())) {
                fields.add(ContentChangedField.CLASSIFICATION_CODE_3);
            }
        }

        private ContentLifecycleChange lifecycleChange(ExistingContent existing) {
            if (existing == null) {
                return ContentLifecycleChange.NEW;
            }
            return ContentLifecycleChange.NONE;
        }

        private boolean isInactive(ExistingContent existing) {
            return existing != null
                    && (!"ACTIVE".equals(existing.status()) || existing.deleted());
        }

        private boolean locationChanged(ExistingContent existing) {
            return existing.longitude() == null
                    || existing.latitude() == null
                    || longitude.compareTo(existing.longitude()) != 0
                    || latitude.compareTo(existing.latitude()) != 0;
        }

        private TourApiContentLocation location() {
            return new TourApiContentLocation(latitude.doubleValue(), longitude.doubleValue());
        }

        private void setValues(PreparedStatement statement) throws SQLException {
            statement.setString(1, SOURCE_PROVIDER);
            statement.setString(2, sourceContentId);
            statement.setString(3, sourceContentTypeId);
            statement.setString(4, title);
            statement.setLong(5, regionId);
            statement.setString(6, sourceRegionCode);
            statement.setString(7, sourceDistrictCode);
            statement.setString(8, classificationCode1);
            statement.setString(9, classificationCode2);
            statement.setString(10, classificationCode3);
            statement.setString(11, address);
            statement.setBigDecimal(12, longitude);
            statement.setBigDecimal(13, latitude);
            statement.setString(14, phone);
            statement.setString(15, thumbnailUrl);
            statement.setTimestamp(16, timestamp(sourceCreatedAt));
            statement.setTimestamp(17, timestamp(sourceModifiedAt));
        }

        private static Timestamp timestamp(LocalDateTime value) {
            return value == null ? null : Timestamp.valueOf(value);
        }
    }
}
