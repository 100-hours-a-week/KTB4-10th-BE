package com.ktb10.kgb.content.repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** TourAPI 원본 생성 시각을 기준으로 신규 행사를 조회합니다. */
@Repository
public class NewEventQuery {

    private static final String LIST_QUERY =
            """
                    SELECT
                        content.source_content_id,
                        content.title,
                        content.address,
                        content.thumbnail_url,
                        event.start_date,
                        event.end_date,
                        content.source_created_at
                    FROM tourism_contents content
                    JOIN event_details event ON event.content_id = content.id
                    WHERE content.status = 'ACTIVE'
                      AND content.deleted_at IS NULL
                      AND content.classification_code_1 = 'EV'
                      AND content.source_created_at >= ?
                      AND content.source_created_at < ?
                    ORDER BY content.source_created_at DESC, content.id DESC
                    LIMIT ? OFFSET ?
            """;

    private static final String COUNT_QUERY =
            """
                    SELECT COUNT(*)
                    FROM tourism_contents content
                    JOIN event_details event ON event.content_id = content.id
                    WHERE content.status = 'ACTIVE'
                      AND content.deleted_at IS NULL
                      AND content.classification_code_1 = 'EV'
                      AND content.source_created_at >= ?
                      AND content.source_created_at < ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public NewEventQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<NewEventData> findBySourceCreatedAt(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive,
            int limit,
            long offset) {
        return jdbcTemplate.query(
                LIST_QUERY,
                (resultSet, rowNumber) -> new NewEventData(
                        resultSet.getString("source_content_id"),
                        resultSet.getString("title"),
                        resultSet.getString("address"),
                        resultSet.getString("thumbnail_url"),
                        toLocalDate(resultSet.getDate("start_date")),
                        toLocalDate(resultSet.getDate("end_date")),
                        resultSet.getTimestamp("source_created_at").toLocalDateTime()),
                Timestamp.valueOf(startInclusive),
                Timestamp.valueOf(endExclusive),
                limit,
                offset);
    }

    public long countBySourceCreatedAt(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        Long count = jdbcTemplate.queryForObject(
                COUNT_QUERY,
                Long.class,
                Timestamp.valueOf(startInclusive),
                Timestamp.valueOf(endExclusive));
        return count == null ? 0L : count;
    }

    private static java.time.LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
