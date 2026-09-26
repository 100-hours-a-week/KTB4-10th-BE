package com.ktb10.kgb.tools.tourapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.guidebook.entity.AdministrativeDistrict;
import com.ktb10.kgb.guidebook.entity.AdministrativeProvince;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 제공받은 TourAPI 목록 JSON을 서비스 테이블에 최초 적재합니다. */
@Service
public class TourApiInitialImportService {

    private static final String SOURCE_PROVIDER = "TOUR_API";
    private static final DateTimeFormatter SOURCE_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter SOURCE_DATE =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public TourApiInitialImportService(
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ImportSummary importFiles(Path areaFile, Path festivalFile) {
        List<JsonNode> contents = readItems(areaFile);
        Map<SourceRegionKey, ServiceRegionKey> regionNames =
                extractRegionNames(contents);
        Map<SourceRegionKey, Long> regionIds = synchronizeRegions(regionNames);

        ContentImportResult contentResult = importContents(contents, regionIds);
        List<JsonNode> events = readItems(festivalFile);
        EventImportResult eventResult = importEvents(events, contentResult.contentIds());

        return new ImportSummary(
                contents.size(),
                contentResult.imported(),
                contentResult.missingCoordinates(),
                contentResult.missingRegionMapping(),
                events.size(),
                eventResult.imported(),
                eventResult.invalid());
    }

    private List<JsonNode> readItems(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("JSON 파일을 찾을 수 없습니다: " + file);
        }
        try {
            JsonNode items = objectMapper.readTree(file.toFile())
                    .path("response")
                    .path("body")
                    .path("items")
                    .path("item");
            if (!items.isArray()) {
                throw new IllegalArgumentException(
                        "TourAPI item 배열을 찾을 수 없습니다: " + file);
            }
            List<JsonNode> result = new ArrayList<>();
            items.forEach(result::add);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("JSON 파일을 읽을 수 없습니다: " + file, exception);
        }
    }

    private Map<SourceRegionKey, ServiceRegionKey> extractRegionNames(
            List<JsonNode> contents) {
        Map<SourceRegionKey, Map<ServiceRegionKey, Integer>> frequencies = new HashMap<>();
        for (JsonNode content : contents) {
            SourceRegionKey sourceKey = sourceRegionKey(content);
            ServiceRegionKey serviceKey = serviceRegionKey(content.path("addr1").asText());
            if (sourceKey == null || serviceKey == null) {
                continue;
            }
            frequencies.computeIfAbsent(sourceKey, ignored -> new HashMap<>())
                    .merge(serviceKey, 1, Integer::sum);
        }

        Map<SourceRegionKey, ServiceRegionKey> result = new HashMap<>();
        frequencies.forEach((source, candidates) -> candidates.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .ifPresent(entry -> result.put(source, entry.getKey())));
        return result;
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
            AdministrativeDistrict.fromDisplayName(province, tokens[1]);
            return new ServiceRegionKey(province, tokens[1]);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Map<SourceRegionKey, Long> synchronizeRegions(
            Map<SourceRegionKey, ServiceRegionKey> regionNames) {
        Map<AdministrativeProvince, String> provinceCodes = new HashMap<>();
        regionNames.forEach((source, service) -> provinceCodes.putIfAbsent(
                service.province(), source.regionCode()));

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
        return mappings;
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
            List<JsonNode> sourceContents,
            Map<SourceRegionKey, Long> regionIds) {
        List<ContentRow> rows = new ArrayList<>();
        Set<String> contentIds = new HashSet<>();
        int missingCoordinates = 0;
        int missingRegionMapping = 0;

        for (JsonNode source : sourceContents) {
            BigDecimal longitude = decimal(source, "mapx");
            BigDecimal latitude = decimal(source, "mapy");
            if (longitude == null || latitude == null) {
                missingCoordinates++;
                continue;
            }
            SourceRegionKey sourceRegion = sourceRegionKey(source);
            Long regionId = regionIds.get(sourceRegion);
            if (regionId == null) {
                missingRegionMapping++;
                continue;
            }
            ContentRow row = ContentRow.from(source, regionId, longitude, latitude);
            if (row != null) {
                rows.add(row);
                contentIds.add(row.sourceContentId());
            }
        }

        batchUpsertContents(rows);
        return new ContentImportResult(
                rows.size(), missingCoordinates, missingRegionMapping, contentIds);
    }

    private void batchUpsertContents(List<ContentRow> rows) {
        String sql =
                """
                INSERT INTO tourism_contents (
                    category, source_provider, source_content_id,
                    source_content_type_id, title, description, region_id,
                    source_region_code, source_district_code,
                    classification_code_1, classification_code_2,
                    classification_code_3, address, location, phone,
                    homepage_url, thumbnail_url, source_created_at,
                    source_modified_at, status, deleted_at, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?,
                    ST_SRID(POINT(?, ?), 4326), ?, NULL, ?, ?, ?,
                    'ACTIVE', NULL, NOW(6), NOW(6)
                )
                ON DUPLICATE KEY UPDATE
                    category = VALUES(category),
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

    private EventImportResult importEvents(
            List<JsonNode> sourceEvents,
            Set<String> importedContentIds) {
        List<EventRow> events = new ArrayList<>();
        int invalid = 0;
        for (JsonNode source : sourceEvents) {
            String contentId = text(source, "contentid");
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
                EventRow event = events.get(index);
                statement.setObject(1, event.startDate());
                statement.setObject(2, event.endDate());
                statement.setString(3, SOURCE_PROVIDER);
                statement.setString(4, event.sourceContentId());
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        });
        return new EventImportResult(events.size(), invalid);
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

    private record SourceRegionKey(String regionCode, String districtCode) {
    }

    private record ServiceRegionKey(
            AdministrativeProvince province,
            String districtName) {
    }

    private record ContentImportResult(
            int imported,
            int missingCoordinates,
            int missingRegionMapping,
            Set<String> contentIds) {
    }

    private record EventImportResult(int imported, int invalid) {
    }

    private record EventRow(
            String sourceContentId,
            LocalDate startDate,
            LocalDate endDate) {
    }

    private record ContentRow(
            String category,
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
            String category = text(source, "lclsSystm1");
            if (sourceContentId == null || title == null || category == null) {
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
                    limit(category, 30),
                    limit(sourceContentId, 100),
                    limit(text(source, "contenttypeid"), 20),
                    limit(title, 200),
                    regionId,
                    limit(text(source, "lDongRegnCd"), 20),
                    limit(text(source, "lDongSignguCd"), 20),
                    limit(text(source, "lclsSystm1"), 20),
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

        private void setValues(PreparedStatement statement) throws SQLException {
            statement.setString(1, category);
            statement.setString(2, SOURCE_PROVIDER);
            statement.setString(3, sourceContentId);
            statement.setString(4, sourceContentTypeId);
            statement.setString(5, title);
            statement.setLong(6, regionId);
            statement.setString(7, sourceRegionCode);
            statement.setString(8, sourceDistrictCode);
            statement.setString(9, classificationCode1);
            statement.setString(10, classificationCode2);
            statement.setString(11, classificationCode3);
            statement.setString(12, address);
            statement.setBigDecimal(13, longitude);
            statement.setBigDecimal(14, latitude);
            statement.setString(15, phone);
            statement.setString(16, thumbnailUrl);
            statement.setTimestamp(17, timestamp(sourceCreatedAt));
            statement.setTimestamp(18, timestamp(sourceModifiedAt));
        }

        private static Timestamp timestamp(LocalDateTime value) {
            return value == null ? null : Timestamp.valueOf(value);
        }
    }
}
