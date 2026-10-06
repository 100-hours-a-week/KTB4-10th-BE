package com.ktb10.kgb.content.repository;

import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.dto.MapContentItemResponse.EventPeriod;
import com.ktb10.kgb.content.dto.MapClusterResponse;
import java.sql.Date;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MySQL 공간 인덱스를 사용해 지도 화면 영역의 공통 콘텐츠를 조회합니다. */
@Repository
public class MapContentQuery {

    private static final List<String> SUPPORTED_CLASSIFICATION_CODES =
            List.of("NA", "HS", "VE", "EX", "LS", "EV");

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
                      OR event.end_date >= CURRENT_DATE
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
                                resultSet.getDate("end_date"))),
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

    public List<MapClusterQueryResult> findClustersWithinBounds(
            double south,
            double west,
            double north,
            double east,
            double gridSize) {
        String sql = """
                WITH ranked AS (
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
                        event.end_date,
                        FLOOR(ST_Latitude(content.location) / %1$f) AS grid_lat,
                        FLOOR(ST_Longitude(content.location) / %1$f) AS grid_lng,
                        COUNT(*) OVER (
                            PARTITION BY
                                FLOOR(ST_Latitude(content.location) / %1$f),
                                FLOOR(ST_Longitude(content.location) / %1$f)
                        ) AS cluster_count,
                        AVG(ST_Latitude(content.location)) OVER (
                            PARTITION BY
                                FLOOR(ST_Latitude(content.location) / %1$f),
                                FLOOR(ST_Longitude(content.location) / %1$f)
                        ) AS cluster_latitude,
                        AVG(ST_Longitude(content.location)) OVER (
                            PARTITION BY
                                FLOOR(ST_Latitude(content.location) / %1$f),
                                FLOOR(ST_Longitude(content.location) / %1$f)
                        ) AS cluster_longitude,
                        ROW_NUMBER() OVER (
                            PARTITION BY
                                FLOOR(ST_Latitude(content.location) / %1$f),
                                FLOOR(ST_Longitude(content.location) / %1$f)
                            ORDER BY
                                CASE
                                    WHEN content.classification_code_1 = 'EV'
                                     AND event.start_date <= CURRENT_DATE
                                     AND event.end_date >= CURRENT_DATE
                                    THEN 0 ELSE 1
                                END,
                                POWER(
                                    ST_Latitude(content.location)
                                      - (FLOOR(ST_Latitude(content.location) / %1$f) * %1$f + %1$f / 2),
                                    2
                                ) + POWER(
                                    ST_Longitude(content.location)
                                      - (FLOOR(ST_Longitude(content.location) / %1$f) * %1$f + %1$f / 2),
                                    2
                                ),
                                content.id
                        ) AS representative_rank
                    FROM tourism_contents content
                    LEFT JOIN event_details event ON event.content_id = content.id
                    WHERE content.status = 'ACTIVE'
                      AND content.deleted_at IS NULL
                      AND content.classification_code_1 IN (?, ?, ?, ?, ?, ?)
                      AND (
                          content.classification_code_1 <> 'EV'
                          OR event.end_date >= CURRENT_DATE
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
                )
                SELECT *
                FROM ranked
                WHERE representative_rank = 1
                ORDER BY cluster_count DESC, grid_lat, grid_lng
                """.formatted(gridSize);

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new MapClusterQueryResult(
                        new MapClusterResponse(
                                resultSet.getString("grid_lat") + ":" + resultSet.getString("grid_lng"),
                                resultSet.getDouble("cluster_latitude"),
                                resultSet.getDouble("cluster_longitude"),
                                resultSet.getInt("cluster_count")),
                        new MapContentCommonData(
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
                                        resultSet.getDate("end_date")))),
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

    private static EventPeriod eventPeriod(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            return null;
        }
        return new EventPeriod(startDate.toLocalDate(), endDate.toLocalDate());
    }
}
