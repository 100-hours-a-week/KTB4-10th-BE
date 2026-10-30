package com.ktb10.kgb.content.tourapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 콘텐츠 한 건의 상세 원본을 화면용 최소 필드로 정규화합니다. */
@Service
public class TourApiDetailImportService {

    private static final DateTimeFormatter SOURCE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TourApiContentChangeOccurrenceRepository changeOccurrenceRepository;

    public TourApiDetailImportService(
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            TourApiContentChangeOccurrenceRepository changeOccurrenceRepository) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.changeOccurrenceRepository = changeOccurrenceRepository;
    }

    @Transactional
    public ContentChangeSet importDetail(
            TourApiDetailTarget target,
            byte[] commonRaw,
            byte[] introRaw) {
        return importDetailValues(target, commonRaw, introRaw);
    }

    @Transactional
    public ContentChangeSet importDetail(
            UUID runId,
            TourApiDetailTarget target,
            byte[] commonRaw,
            byte[] introRaw) {
        ContentChangeSet changeSet = importDetailValues(target, commonRaw, introRaw);
        changeOccurrenceRepository.merge(runId, List.of(changeSet));
        return changeSet;
    }

    private ContentChangeSet importDetailValues(
            TourApiDetailTarget target,
            byte[] commonRaw,
            byte[] introRaw) {
        JsonNode common = firstItem(commonRaw, "detailCommon2");
        JsonNode intro = target.event() ? firstItem(introRaw, "detailIntro2") : null;
        DetailValues values = DetailValues.from(common, intro);
        ExistingDetail existing = loadExisting(target);
        int updatedContents = jdbcTemplate.update(
                """
                UPDATE tourism_contents
                SET description = ?, homepage_url = ?, phone = ?, updated_at = NOW(6)
                WHERE id = ?
                """,
                values.description(),
                values.homepageUrl(),
                values.phone(),
                target.id());
        if (updatedContents != 1) {
            throw new IllegalStateException(
                    "상세 적재 대상 콘텐츠를 찾을 수 없습니다: contentId="
                            + target.sourceContentId());
        }

        if (!target.event()) {
            return values.changeSet(target.sourceContentId(), existing, false);
        }
        LocalDate startDate = values.startDate();
        LocalDate endDate = values.endDate();
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new TourApiException(
                    "detailIntro2 행사 기간이 올바르지 않습니다: contentId="
                            + target.sourceContentId(),
                    false);
        }
        int updatedEvents = jdbcTemplate.update(
                """
                INSERT INTO event_details (
                    content_id, start_date, end_date, operating_hours, organizer,
                    organizer_phone, event_place, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, NOW(6))
                ON DUPLICATE KEY UPDATE
                    start_date = VALUES(start_date), end_date = VALUES(end_date),
                    operating_hours = VALUES(operating_hours), organizer = VALUES(organizer),
                    organizer_phone = VALUES(organizer_phone), event_place = VALUES(event_place),
                    updated_at = NOW(6)
                """,
                target.id(),
                startDate,
                endDate,
                values.operatingHours(),
                values.organizer(),
                values.organizerPhone(),
                values.eventPlace());
        if (updatedEvents < 1) {
            throw new IllegalStateException(
                    "행사 상세 기본 행을 찾을 수 없습니다: contentId="
                            + target.sourceContentId());
        }
        return values.changeSet(target.sourceContentId(), existing, true);
    }

    private ExistingDetail loadExisting(TourApiDetailTarget target) {
        return jdbcTemplate.query(
                """
                SELECT content.description, content.homepage_url, content.phone,
                       ST_Longitude(content.location) AS longitude,
                       ST_Latitude(content.location) AS latitude,
                       event.start_date, event.end_date, event.operating_hours,
                       event.organizer, event.organizer_phone, event.event_place
                FROM tourism_contents content
                LEFT JOIN event_details event ON event.content_id = content.id
                WHERE content.id = ?
                """,
                (resultSet, rowNumber) -> new ExistingDetail(
                        resultSet.getString("description"),
                        resultSet.getString("homepage_url"),
                        resultSet.getString("phone"),
                        resultSet.getDouble("latitude"),
                        resultSet.getDouble("longitude"),
                        resultSet.getDate("start_date") == null
                                ? null
                                : resultSet.getDate("start_date").toLocalDate(),
                        resultSet.getDate("end_date") == null
                                ? null
                                : resultSet.getDate("end_date").toLocalDate(),
                        resultSet.getString("operating_hours"),
                        resultSet.getString("organizer"),
                        resultSet.getString("organizer_phone"),
                        resultSet.getString("event_place")),
                target.id()).stream().findFirst().orElseThrow(() -> new IllegalStateException(
                        "상세 적재 대상 콘텐츠를 찾을 수 없습니다: contentId="
                                + target.sourceContentId()));
    }

    private JsonNode firstItem(byte[] raw, String source) {
        List<JsonNode> items = items(raw, source);
        if (items.isEmpty()) {
            throw new IllegalArgumentException(source + " 응답에 item이 없습니다.");
        }
        return items.getFirst();
    }

    private List<JsonNode> items(byte[] raw, String source) {
        if (raw == null) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode response = root.path("response");
            String resultCode = response.path("header").path("resultCode").asText(null);
            if (resultCode == null) {
                resultCode = root.path("resultCode").asText(null);
            }
            if (resultCode != null && !"0000".equals(resultCode)) {
                throw new TourApiException(
                        source + " 오류 응답: resultCode=" + resultCode,
                        false);
            }
            JsonNode node = response.path("body").path("items").path("item");
            if (node.isMissingNode() || node.isNull() || node.isTextual()) {
                return List.of();
            }
            if (!node.isArray()) {
                throw new IllegalArgumentException(source + " 응답의 item이 배열이 아닙니다.");
            }
            return objectMapper.convertValue(
                    node,
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, JsonNode.class));
        } catch (IOException exception) {
            throw new UncheckedIOException(source + " JSON을 읽을 수 없습니다.", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
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
        return value == null || value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }

    private record ExistingDetail(
            String description,
            String homepageUrl,
            String phone,
            double latitude,
            double longitude,
            LocalDate startDate,
            LocalDate endDate,
            String operatingHours,
            String organizer,
            String organizerPhone,
            String eventPlace) {

        private TourApiContentLocation location() {
            return new TourApiContentLocation(latitude, longitude);
        }
    }

    private record DetailValues(
            String description,
            String homepageUrl,
            String phone,
            LocalDate startDate,
            LocalDate endDate,
            String operatingHours,
            String organizer,
            String organizerPhone,
            String eventPlace) {

        private static DetailValues from(JsonNode common, JsonNode intro) {
            return new DetailValues(
                    text(common, "overview"),
                    text(common, "homepage"),
                    text(common, "tel"),
                    intro == null ? null : date(intro, "eventstartdate"),
                    intro == null ? null : date(intro, "eventenddate"),
                    intro == null ? null : limit(text(intro, "playtime"), 255),
                    intro == null ? null : limit(text(intro, "sponsor1"), 200),
                    intro == null ? null : limit(text(intro, "sponsor1tel"), 100),
                    intro == null ? null : limit(text(intro, "eventplace"), 500));
        }

        private ContentChangeSet changeSet(
                String sourceContentId,
                ExistingDetail existing,
                boolean event) {
            EnumSet<ContentChangedField> fields = EnumSet.noneOf(ContentChangedField.class);
            addIfChanged(fields, ContentChangedField.DESCRIPTION, description, existing.description());
            addIfChanged(fields, ContentChangedField.HOMEPAGE_URL, homepageUrl, existing.homepageUrl());
            addIfChanged(fields, ContentChangedField.PHONE, phone, existing.phone());
            if (event) {
                addEventChanges(fields, existing);
            }
            return ContentChangeSet.of(
                    sourceContentId,
                    fields,
                    ContentLifecycleChange.NONE,
                    null,
                    existing.location());
        }

        private void addEventChanges(
                Set<ContentChangedField> fields,
                ExistingDetail existing) {
            addIfChanged(fields, ContentChangedField.START_DATE, startDate, existing.startDate());
            addIfChanged(fields, ContentChangedField.END_DATE, endDate, existing.endDate());
            addIfChanged(fields, ContentChangedField.OPERATING_HOURS,
                    operatingHours, existing.operatingHours());
            addIfChanged(fields, ContentChangedField.ORGANIZER, organizer, existing.organizer());
            addIfChanged(fields, ContentChangedField.ORGANIZER_PHONE,
                    organizerPhone, existing.organizerPhone());
            addIfChanged(fields, ContentChangedField.EVENT_PLACE, eventPlace, existing.eventPlace());
        }

        private static void addIfChanged(
                Set<ContentChangedField> fields,
                ContentChangedField field,
                Object current,
                Object previous) {
            if (!Objects.equals(current, previous)) {
                fields.add(field);
            }
        }
    }
}
