package com.ktb10.kgb.guidebook.client;

import com.ktb10.kgb.content.model.TourismContentCategoryPolicy;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest.Content;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest.ContentType;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest.Coordinates;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest.EventPeriod;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** AI 생성 요청에 포함할 활성 관광 콘텐츠를 요청 지역으로 조회합니다. */
@Component
public class AiGuidebookContentQuery {

    static final int MAX_CANDIDATE_CONTENTS = 100;
    private static final List<String> ALLOWED_PLACE_CLASSIFICATION_CODES =
            TourismContentCategoryPolicy.allowedSourceCodes();
    private static final String ALLOWED_PLACE_CLASSIFICATION_PLACEHOLDERS =
            String.join(", ", ALLOWED_PLACE_CLASSIFICATION_CODES.stream()
                    .map(ignored -> "?")
                    .toList());

    private final JdbcTemplate jdbcTemplate;

    public AiGuidebookContentQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Content> findAll(
            String province,
            String city,
            LocalDate startDate,
            LocalDate endDate) {
        Object[] arguments = new Object[ALLOWED_PLACE_CLASSIFICATION_CODES.size() + 3];
        arguments[0] = province;
        arguments[1] = city;
        int index = 2;
        for (String code : ALLOWED_PLACE_CLASSIFICATION_CODES) {
            arguments[index++] = code;
        }
        arguments[index] = MAX_CANDIDATE_CONTENTS;
        return jdbcTemplate.query(
                """
                SELECT
                    content.source_content_id,
                    content.title,
                    content.description,
                    content.classification_code_1,
                    content.classification_code_2,
                    content.classification_code_3,
                    content.address,
                    ST_Longitude(content.location) AS longitude,
                    ST_Latitude(content.location) AS latitude,
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
                  AND (
                      content.classification_code_1 = 'EV'
                      OR content.classification_code_2 IN (%s)
                  )
                ORDER BY content.id
                LIMIT ?
                """.formatted(ALLOWED_PLACE_CLASSIFICATION_PLACEHOLDERS),
                (resultSet, rowNumber) -> new Content(
                        resultSet.getString("source_content_id"),
                        "EV".equals(resultSet.getString("classification_code_1"))
                                ? ContentType.EVENT
                                : ContentType.PLACE,
                        resultSet.getString("title"),
                        AiContentOverviewFormatter.format(resultSet.getString("description")),
                        resultSet.getString("classification_code_1"),
                        resultSet.getString("classification_code_2"),
                        resultSet.getString("classification_code_3"),
                        resultSet.getString("address"),
                        new Coordinates(
                                resultSet.getDouble("latitude"),
                                resultSet.getDouble("longitude")),
                        resultSet.getString("thumbnail_url"),
                        eventPeriod(
                                resultSet.getDate("start_date"),
                                resultSet.getDate("end_date"))),
                arguments);
    }

    private static EventPeriod eventPeriod(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            return null;
        }
        return new EventPeriod(startDate.toLocalDate(), endDate.toLocalDate());
    }

}
