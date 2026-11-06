package com.ktb10.kgb.content.repository;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 회원이 보관한 가이드북의 일정 관광 콘텐츠 식별자를 조회합니다. */
@Repository
public class MapGuidebookContentQuery {

    private final JdbcTemplate jdbcTemplate;

    public MapGuidebookContentQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Set<Long>> findActiveContentIds(Long memberId, Long guidebookId) {
        Set<Long> contentIds = new LinkedHashSet<>();
        int[] matchedRelationships = {0};
        jdbcTemplate.query(
                """
                SELECT
                    member_guidebook.guidebook_id,
                    item.tourism_content_id
                FROM member_guidebooks member_guidebook
                LEFT JOIN itinerary_days day
                  ON day.guidebook_id = member_guidebook.guidebook_id
                LEFT JOIN itinerary_items item
                  ON item.itinerary_day_id = day.id
                WHERE member_guidebook.member_id = ?
                  AND member_guidebook.guidebook_id = ?
                  AND member_guidebook.deleted_at IS NULL
                ORDER BY day.day_number, item.sequence
                """,
                resultSet -> {
                    matchedRelationships[0]++;
                    Long contentId = resultSet.getObject("tourism_content_id", Long.class);
                    if (contentId != null) {
                        contentIds.add(contentId);
                    }
                },
                memberId,
                guidebookId);
        if (matchedRelationships[0] == 0) {
            return Optional.empty();
        }
        return Optional.of(Set.copyOf(contentIds));
    }
}
