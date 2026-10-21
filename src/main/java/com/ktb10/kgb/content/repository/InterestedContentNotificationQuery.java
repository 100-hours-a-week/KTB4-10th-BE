package com.ktb10.kgb.content.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 변경된 행사를 관심 등록한 활성 회원을 일괄 조회합니다. */
@Repository
public class InterestedContentNotificationQuery {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InterestedContentNotificationQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<InterestedContentNotificationTarget> findTargets(
            Collection<String> sourceContentIds) {
        if (sourceContentIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT
                    favorite.member_id,
                    content.source_content_id,
                    content.title
                FROM favorite_contents favorite
                JOIN tourism_contents content ON content.id = favorite.content_id
                JOIN members member ON member.id = favorite.member_id
                WHERE content.source_provider = 'TOUR_API'
                  AND content.source_content_id IN (:sourceContentIds)
                  AND content.classification_code_1 = 'EV'
                  AND content.status = 'ACTIVE'
                  AND content.deleted_at IS NULL
                  AND member.status = 'ACTIVE'
                  AND member.deleted_at IS NULL
                ORDER BY favorite.member_id, content.id
                """,
                Map.of("sourceContentIds", sourceContentIds),
                (resultSet, rowNumber) -> new InterestedContentNotificationTarget(
                        resultSet.getLong("member_id"),
                        resultSet.getString("source_content_id"),
                        resultSet.getString("title")));
    }
}
