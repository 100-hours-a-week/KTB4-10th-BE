package com.ktb10.kgb.content.repository;

import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.dto.MapContentItemResponse.EventPeriod;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** MySQL 공간 인덱스를 사용해 지도 화면 영역의 공통 콘텐츠를 조회합니다. */
@Repository
public class MapContentQuery {

    private static final List<String> SUPPORTED_CLASSIFICATION_CODES =
            List.of("NA", "HS", "VE", "EX", "LS", "EV");
    private static final RowMapper<MapContentCommonData> COMMON_CONTENT_ROW_MAPPER =
            (resultSet, rowNumber) -> new MapContentCommonData(
                    resultSet.getLong("id"),
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
                            resultSet.getDate("end_date")));

    private final JdbcTemplate jdbcTemplate;

    public MapContentQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MapContentCommonData> findWithinBounds(
            double south,
            double west,
            double north,
            double east) {
        return jdbcTemplate.query(
                """
                SELECT
                    content.id,
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
                  AND content.classification_code_1 IN (?, ?, ?, ?, ?, ?)
                  AND (
                      content.classification_code_1 <> 'EV'
                      OR event.end_date >= DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR)
                  )
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
                """,
                COMMON_CONTENT_ROW_MAPPER,
                SUPPORTED_CLASSIFICATION_CODES.get(0),
                SUPPORTED_CLASSIFICATION_CODES.get(1),
                SUPPORTED_CLASSIFICATION_CODES.get(2),
                SUPPORTED_CLASSIFICATION_CODES.get(3),
                SUPPORTED_CLASSIFICATION_CODES.get(4),
                SUPPORTED_CLASSIFICATION_CODES.get(5),
                west, south,
                east, south,
                east, north,
                west, north,
                west, south);
    }

    public List<MapContentCommonData> findAllCacheable() {
        return jdbcTemplate.query(
                """
                SELECT
                    content.id,
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
                  AND content.classification_code_1 IN (?, ?, ?, ?, ?, ?)
                  AND (
                      content.classification_code_1 <> 'EV'
                      OR event.end_date >= DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR)
                  )
                ORDER BY content.id
                """,
                COMMON_CONTENT_ROW_MAPPER,
                SUPPORTED_CLASSIFICATION_CODES.toArray());
    }

    /** 기준일에 종료되어 오늘 지도 노출 대상에서 빠지는 행사 위치를 조회합니다. */
    public List<MapContentCommonData> findEventsEndingOn(LocalDate endDate) {
        return jdbcTemplate.query(
                """
                SELECT
                    content.id,
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
                JOIN event_details event ON event.content_id = content.id
                WHERE content.status = 'ACTIVE'
                  AND content.deleted_at IS NULL
                  AND content.classification_code_1 = 'EV'
                  AND event.end_date = ?
                ORDER BY content.id
                """,
                COMMON_CONTENT_ROW_MAPPER,
                endDate);
    }

    private static EventPeriod eventPeriod(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            return null;
        }
        return new EventPeriod(startDate.toLocalDate(), endDate.toLocalDate());
    }
}
