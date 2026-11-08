package com.ktb10.kgb.content.repository;

import com.ktb10.kgb.content.model.ContentSearchCategory;
import com.ktb10.kgb.content.model.TourismContentCategoryPolicy;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 정보 탭 목록과 지도 검색 결과를 MySQL에서 번호 페이지로 조회합니다. */
@Repository
public class ContentSearchQuery {

    private static final String BASE_FROM =
            """
            FROM tourism_contents content
            JOIN regions region ON region.id = content.region_id
            JOIN regions province ON province.id = COALESCE(region.parent_id, region.id)
            LEFT JOIN event_details event ON event.content_id = content.id
            WHERE content.status = 'ACTIVE'
              AND content.deleted_at IS NULL
              AND content.location IS NOT NULL
              AND %s
              AND (:query IS NULL OR LOCATE(:query, content.title) > 0)
              AND (:regionCode IS NULL OR province.administrative_code = :regionCode)
              AND (
                    :monthStart IS NULL
                    OR content.classification_code_1 <> 'EV'
                    OR (event.start_date <= :monthEnd AND event.end_date >= :monthStart)
              )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ContentSearchQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ContentSearchPage findContents(
            ContentSearchCategory category,
            String query,
            String regionCode,
            LocalDate monthStart,
            LocalDate monthEnd,
            int page,
            int size) {
        String from = BASE_FROM.formatted(categoryCondition(category));
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("allowedSourceCodes", TourismContentCategoryPolicy.allowedSourceCodes())
                .addValue("query", query)
                .addValue("regionCode", regionCode)
                .addValue("monthStart", monthStart)
                .addValue("monthEnd", monthEnd)
                .addValue("limit", size)
                .addValue("offset", (page - 1) * size);

        String select =
                """
                SELECT content.id AS id,
                       content.source_content_id AS content_id,
                       content.title AS title,
                       CASE WHEN content.classification_code_1 = 'EV'
                            THEN 'EVENT' ELSE 'PLACE' END AS content_type,
                       province.administrative_code AS region_code,
                       province.name AS region_name,
                       content.address AS address,
                       ST_Latitude(content.location) AS latitude,
                       ST_Longitude(content.location) AS longitude,
                       content.thumbnail_url AS thumbnail_url,
                       event.start_date AS start_date,
                       event.end_date AS end_date
                """ + from + " ORDER BY content.created_at DESC, content.id DESC"
                        + " LIMIT :limit OFFSET :offset";
        List<ContentSearchItem> items = jdbcTemplate.query(select, parameters, (result, row) ->
                new ContentSearchItem(
                        result.getLong("id"),
                        result.getString("content_id"),
                        result.getString("title"),
                        result.getString("content_type"),
                        result.getString("region_code"),
                        result.getString("region_name"),
                        result.getString("address"),
                        result.getDouble("latitude"),
                        result.getDouble("longitude"),
                        result.getString("thumbnail_url"),
                        result.getObject("start_date", LocalDate.class),
                        result.getObject("end_date", LocalDate.class)));
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + from,
                parameters,
                Long.class);
        return new ContentSearchPage(items, total == null ? 0 : total);
    }

    private String categoryCondition(ContentSearchCategory category) {
        String event =
                """
                content.classification_code_1 = 'EV'
                AND event.start_date IS NOT NULL
                AND event.end_date >= DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR)
                """;
        String place =
                """
                (content.classification_code_1 IS NULL
                 OR content.classification_code_1 <> 'EV')
                AND content.classification_code_2 IN (:allowedSourceCodes)
                """;
        return switch (category) {
            case EVENT -> "(" + event + ")";
            case PLACE -> "(" + place + ")";
            case ALL -> "((" + event + ") OR (" + place + "))";
        };
    }

    public record ContentSearchPage(List<ContentSearchItem> items, long totalItems) {
        public ContentSearchPage {
            items = List.copyOf(items);
        }
    }

    public record ContentSearchItem(
            Long id,
            String contentId,
            String title,
            String contentType,
            String regionCode,
            String regionName,
            String address,
            double latitude,
            double longitude,
            String thumbnailUrl,
            LocalDate startDate,
            LocalDate endDate) {
    }
}
