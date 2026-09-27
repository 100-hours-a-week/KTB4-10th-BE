package com.ktb10.kgb.content.repository;

import com.ktb10.kgb.content.dto.MapContentItemResponse;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.dto.MapContentItemResponse.EventPeriod;
import java.sql.Date;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MySQL 공간 인덱스를 사용해 지도 화면 영역의 콘텐츠를 조회합니다. */
@Repository
public class MapContentQuery {

    private final JdbcTemplate jdbcTemplate;

    public MapContentQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MapContentItemResponse> findWithinBounds(
            double south,
            double west,
            double north,
            double east,
            int fetchSize) {
        return jdbcTemplate.query(
                """
                SELECT
                    content.source_content_id,
                    content.title,
                    content.classification_code_1,
                    content.address,
                    ST_Latitude(content.location) AS latitude,
                    ST_Longitude(content.location) AS longitude,
                    content.thumbnail_url,
                    event.start_date,
                    event.end_date
                FROM tourism_contents content
                LEFT JOIN event_details event ON event.content_id = content.id
                WHERE content.status = 'ACTIVE'
                  AND content.deleted_at IS NULL
                  AND MBRContains(
                      ST_GeomFromText(
                          CONCAT(
                              'POLYGON((',
                              ?, ' ', ?, ',',
                              ?, ' ', ?, ',',
                              ?, ' ', ?, ',',
                              ?, ' ', ?, ',',
                              ?, ' ', ?,
                              '))'),
                          4326,
                          'axis-order=long-lat'),
                      content.location)
                ORDER BY content.id
                LIMIT ?
                """,
                (resultSet, rowNumber) -> new MapContentItemResponse(
                        resultSet.getString("source_content_id"),
                        resultSet.getString("title"),
                        "EV".equals(resultSet.getString("classification_code_1"))
                                ? ContentType.EVENT
                                : ContentType.PLACE,
                        resultSet.getString("address"),
                        resultSet.getDouble("latitude"),
                        resultSet.getDouble("longitude"),
                        resultSet.getString("thumbnail_url"),
                        eventPeriod(
                                resultSet.getDate("start_date"),
                                resultSet.getDate("end_date"))),
                west, south,
                east, south,
                east, north,
                west, north,
                west, south,
                fetchSize);
    }

    private static EventPeriod eventPeriod(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            return null;
        }
        return new EventPeriod(startDate.toLocalDate(), endDate.toLocalDate());
    }
}

