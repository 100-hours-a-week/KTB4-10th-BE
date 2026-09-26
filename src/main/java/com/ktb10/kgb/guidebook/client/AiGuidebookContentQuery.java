package com.ktb10.kgb.guidebook.client;

import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest.Content;
import java.sql.Date;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** AI 생성 요청에 포함할 활성 관광 콘텐츠를 요청 지역으로 조회합니다. */
@Component
public class AiGuidebookContentQuery {

    private final JdbcTemplate jdbcTemplate;

    public AiGuidebookContentQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Content> findAll(String province, String city) {
        return jdbcTemplate.query(
                """
                SELECT
                    content.source_content_id,
                    content.title,
                    content.category,
                    content.classification_code_1,
                    content.classification_code_2,
                    content.classification_code_3,
                    content.address,
                    ST_X(content.location) AS longitude,
                    ST_Y(content.location) AS latitude,
                    content.thumbnail_url,
                    event.start_date,
                    event.end_date
                FROM tourism_contents content
                JOIN regions district ON district.id = content.region_id
                JOIN regions province ON province.id = district.parent_id
                LEFT JOIN event_details event ON event.content_id = content.id
                WHERE province.region_level = 'PROVINCE'
                  AND district.region_level = 'DISTRICT'
                  AND province.name = ?
                  AND district.name = ?
                  AND content.status = 'ACTIVE'
                  AND content.deleted_at IS NULL
                ORDER BY content.id
                """,
                (resultSet, rowNumber) -> new Content(
                        resultSet.getString("source_content_id"),
                        resultSet.getString("title"),
                        resultSet.getString("category"),
                        resultSet.getString("classification_code_1"),
                        resultSet.getString("classification_code_2"),
                        resultSet.getString("classification_code_3"),
                        resultSet.getString("address"),
                        resultSet.getDouble("longitude"),
                        resultSet.getDouble("latitude"),
                        resultSet.getString("thumbnail_url"),
                        toLocalDate(resultSet.getDate("start_date")),
                        toLocalDate(resultSet.getDate("end_date"))),
                province,
                city);
    }

    private static java.time.LocalDate toLocalDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }
}
