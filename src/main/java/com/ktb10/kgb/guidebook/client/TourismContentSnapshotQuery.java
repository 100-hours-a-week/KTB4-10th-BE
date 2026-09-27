package com.ktb10.kgb.guidebook.client;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** AI가 선택한 원본 콘텐츠를 일정 스냅샷 저장 정보로 조회합니다. */
@Component
public class TourismContentSnapshotQuery {

    private final JdbcTemplate jdbcTemplate;

    public TourismContentSnapshotQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<TourismContentSnapshot> findBySourceContentId(String sourceContentId) {
        return jdbcTemplate.query(
                        """
                        SELECT
                            content.id,
                            content.source_content_id,
                            content.title,
                            content.category,
                            content.address,
                            ST_X(content.location) AS longitude,
                            ST_Y(content.location) AS latitude,
                            content.thumbnail_url,
                            event.start_date,
                            event.end_date
                        FROM tourism_contents content
                        LEFT JOIN event_details event ON event.content_id = content.id
                        WHERE content.source_provider = 'TOUR_API'
                          AND content.source_content_id = ?
                        """,
                        (resultSet, rowNumber) -> new TourismContentSnapshot(
                                resultSet.getLong("id"),
                                resultSet.getString("source_content_id"),
                                resultSet.getString("title"),
                                resultSet.getString("category"),
                                resultSet.getString("address"),
                                resultSet.getDouble("latitude"),
                                resultSet.getDouble("longitude"),
                                resultSet.getString("thumbnail_url"),
                                toLocalDate(resultSet.getDate("start_date")),
                                toLocalDate(resultSet.getDate("end_date"))),
                        sourceContentId)
                .stream()
                .findFirst();
    }

    private static LocalDate toLocalDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    public record TourismContentSnapshot(
            long id,
            String contentId,
            String name,
            String category,
            String address,
            double latitude,
            double longitude,
            String imageUrl,
            LocalDate eventStartDate,
            LocalDate eventEndDate) {
    }
}
