package com.ktb10.kgb.content.repository;

import java.sql.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 정보 탭의 행사 목록과 상세 데이터를 조회합니다. */
@Repository
public class ContentReadQuery {

    private static final String LIST_QUERY =
            """
                    SELECT content.source_content_id, content.title,
                           region.administrative_code, region.name AS region_name,
                           content.address, content.thumbnail_url,
                           event.start_date, event.end_date
                    FROM tourism_contents content
                    JOIN regions region ON region.id = content.region_id
                    JOIN event_details event ON event.content_id = content.id
                    WHERE content.status = 'ACTIVE'
                      AND content.deleted_at IS NULL
                      AND content.classification_code_1 = 'EV'
            """;
    private static final String COUNT_QUERY =
            """
                    SELECT COUNT(*)
                    FROM tourism_contents content
                    JOIN event_details event ON event.content_id = content.id
                    WHERE content.status = 'ACTIVE'
                      AND content.deleted_at IS NULL
                      AND content.classification_code_1 = 'EV'
            """;

    private final JdbcTemplate jdbcTemplate;

    public ContentReadQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ContentListData> findEvents(String query, int limit, long offset) {
        String searchCondition = query == null ? "" : " AND LOCATE(?, content.title) > 0";
        String sql = LIST_QUERY + searchCondition
                + " ORDER BY content.created_at DESC, content.id DESC LIMIT ? OFFSET ?";
        Object[] arguments = query == null
                ? new Object[] {limit, offset}
                : new Object[] {query, limit, offset};
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new ContentListData(
                resultSet.getString("source_content_id"),
                resultSet.getString("title"),
                resultSet.getString("administrative_code"),
                resultSet.getString("region_name"),
                resultSet.getString("address"),
                resultSet.getString("thumbnail_url"),
                toLocalDate(resultSet.getDate("start_date")),
                toLocalDate(resultSet.getDate("end_date"))), arguments);
    }

    public long countEvents(String query) {
        String searchCondition = query == null ? "" : " AND LOCATE(?, content.title) > 0";
        String sql = COUNT_QUERY + searchCondition;
        Long count = query == null
                ? jdbcTemplate.queryForObject(sql, Long.class)
                : jdbcTemplate.queryForObject(sql, Long.class, query);
        return count == null ? 0L : count;
    }

    public Optional<ContentDetailData> findEventDetail(String contentId) {
        List<ContentDetailData> result = jdbcTemplate.query(
                """
                SELECT content.id, content.source_content_id, content.title,
                       region.administrative_code, region.name AS region_name,
                       ST_Latitude(content.location) AS latitude,
                       ST_Longitude(content.location) AS longitude,
                       content.thumbnail_url, content.description, content.address,
                       content.phone, content.homepage_url,
                       event.start_date, event.end_date,
                       event.operating_hours, event.organizer
                FROM tourism_contents content
                JOIN regions region ON region.id = content.region_id
                JOIN event_details event ON event.content_id = content.id
                WHERE content.source_content_id = ?
                  AND content.status = 'ACTIVE'
                  AND content.deleted_at IS NULL
                  AND content.classification_code_1 = 'EV'
                """,
                (resultSet, rowNumber) -> new ContentDetailData(
                        resultSet.getLong("id"),
                        resultSet.getString("source_content_id"),
                        resultSet.getString("title"),
                        resultSet.getString("administrative_code"),
                        resultSet.getString("region_name"),
                        resultSet.getDouble("latitude"),
                        resultSet.getDouble("longitude"),
                        resultSet.getString("thumbnail_url"),
                        resultSet.getString("description"),
                        resultSet.getString("address"),
                        resultSet.getString("phone"),
                        resultSet.getString("homepage_url"),
                        toLocalDate(resultSet.getDate("start_date")),
                        toLocalDate(resultSet.getDate("end_date")),
                        resultSet.getString("operating_hours"),
                        resultSet.getString("organizer")),
                contentId);
        return result.stream().findFirst();
    }

    public List<ContentImageData> findImages(long contentId) {
        return jdbcTemplate.query(
                """
                SELECT id, image_url, sort_order
                FROM content_images
                WHERE content_id = ?
                ORDER BY sort_order, id
                """,
                (resultSet, rowNumber) -> new ContentImageData(
                        String.valueOf(resultSet.getLong("id")),
                        resultSet.getString("image_url"),
                        resultSet.getInt("sort_order")),
                contentId);
    }

    private static java.time.LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
