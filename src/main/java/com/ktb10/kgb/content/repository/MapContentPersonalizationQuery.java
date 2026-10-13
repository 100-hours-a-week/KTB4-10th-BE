package com.ktb10.kgb.content.repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 지도 응답에 필요한 즐겨찾기 및 가이드북 포함 여부를 일괄 조회합니다. */
@Repository
public class MapContentPersonalizationQuery {

    private final JdbcTemplate jdbcTemplate;

    public MapContentPersonalizationQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MapContentPersonalization findByMemberAndContentIds(
            Long memberId,
            List<Long> contentIds) {
        if (contentIds.isEmpty()) {
            return MapContentPersonalization.empty();
        }

        String placeholders = contentIds.stream()
                .map(ignored -> "?")
                .collect(Collectors.joining(", "));
        final String sql =
                """
                SELECT favorite.content_id, 'FAVORITE' AS relation_type
                FROM favorite_contents favorite
                WHERE favorite.member_id = ?
                  AND favorite.content_id IN (%1$s)
                UNION ALL
                SELECT item.tourism_content_id AS content_id, 'GUIDEBOOK' AS relation_type
                FROM itinerary_items item
                JOIN itinerary_days day ON day.id = item.itinerary_day_id
                JOIN member_guidebooks member_guidebook
                  ON member_guidebook.guidebook_id = day.guidebook_id
                WHERE member_guidebook.member_id = ?
                  AND member_guidebook.deleted_at IS NULL
                  AND item.tourism_content_id IN (%1$s)
                GROUP BY item.tourism_content_id
                """.formatted(placeholders);

        Object[] arguments = new Object[contentIds.size() * 2 + 2];
        int index = 0;
        arguments[index++] = memberId;
        for (Long contentId : contentIds) {
            arguments[index++] = contentId;
        }
        arguments[index++] = memberId;
        for (Long contentId : contentIds) {
            arguments[index++] = contentId;
        }

        Set<Long> favoriteIds = new HashSet<>();
        Set<Long> guidebookIds = new HashSet<>();
        jdbcTemplate.query(sql, resultSet -> {
            Long contentId = resultSet.getLong("content_id");
            if ("FAVORITE".equals(resultSet.getString("relation_type"))) {
                favoriteIds.add(contentId);
            } else {
                guidebookIds.add(contentId);
            }
        }, arguments);
        return new MapContentPersonalization(favoriteIds, guidebookIds);
    }
}
