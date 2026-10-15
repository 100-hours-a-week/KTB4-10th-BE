package com.ktb10.kgb.content.tourapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
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

    public TourApiDetailImportService(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void importDetail(
            TourApiDetailTarget target,
            byte[] commonRaw,
            byte[] introRaw) {
        JsonNode common = firstItem(commonRaw, "detailCommon2");
        int updatedContents = jdbcTemplate.update(
                """
                UPDATE tourism_contents
                SET description = ?, homepage_url = ?, phone = ?, updated_at = NOW(6)
                WHERE id = ?
                """,
                text(common, "overview"),
                text(common, "homepage"),
                text(common, "tel"),
                target.id());
        if (updatedContents != 1) {
            throw new IllegalStateException(
                    "상세 적재 대상 콘텐츠를 찾을 수 없습니다: contentId="
                            + target.sourceContentId());
        }

        if (!target.event()) {
            return;
        }
        JsonNode intro = firstItem(introRaw, "detailIntro2");
        LocalDate startDate = date(intro, "eventstartdate");
        LocalDate endDate = date(intro, "eventenddate");
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
                limit(text(intro, "playtime"), 255),
                limit(text(intro, "sponsor1"), 200),
                limit(text(intro, "sponsor1tel"), 100),
                limit(text(intro, "eventplace"), 500));
        if (updatedEvents < 1) {
            throw new IllegalStateException(
                    "행사 상세 기본 행을 찾을 수 없습니다: contentId="
                            + target.sourceContentId());
        }
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
}
