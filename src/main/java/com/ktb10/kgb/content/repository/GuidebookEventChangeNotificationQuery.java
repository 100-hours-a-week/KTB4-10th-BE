package com.ktb10.kgb.content.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 변경 행사를 포함한 활성 생성자 소유 가이드북을 조회합니다. */
@Repository
public class GuidebookEventChangeNotificationQuery {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GuidebookEventChangeNotificationQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<GuidebookEventChangeNotificationTarget> findTargets(
            Collection<String> sourceContentIds) {
        if (sourceContentIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT DISTINCT
                    member_guidebook.member_id,
                    member_guidebook.guidebook_id,
                    content.source_content_id
                FROM tourism_contents content
                JOIN itinerary_items item ON item.tourism_content_id = content.id
                JOIN itinerary_days itinerary_day
                  ON itinerary_day.id = item.itinerary_day_id
                JOIN guidebooks guidebook ON guidebook.id = itinerary_day.guidebook_id
                JOIN member_guidebooks member_guidebook
                  ON member_guidebook.guidebook_id = guidebook.id
                JOIN members member ON member.id = member_guidebook.member_id
                WHERE content.source_provider = 'TOUR_API'
                  AND content.source_content_id IN (:sourceContentIds)
                  AND content.classification_code_1 = 'EV'
                  AND member_guidebook.acquisition_type = 'CREATED'
                  AND member_guidebook.deleted_at IS NULL
                  AND guidebook.content_html IS NOT NULL
                  AND member.status = 'ACTIVE'
                  AND member.deleted_at IS NULL
                ORDER BY member_guidebook.member_id,
                         member_guidebook.guidebook_id,
                         content.source_content_id
                """,
                Map.of("sourceContentIds", sourceContentIds),
                (resultSet, rowNumber) -> new GuidebookEventChangeNotificationTarget(
                        resultSet.getLong("member_id"),
                        resultSet.getLong("guidebook_id"),
                        resultSet.getString("source_content_id")));
    }
}
