package com.ktb10.kgb.content.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 지도 핀의 관심 장소 등록 대상 확인과 저장·삭제를 수행합니다. */
@Repository
public class FavoriteContentQuery {

    private final JdbcTemplate jdbcTemplate;

    public FavoriteContentQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> findActiveContentId(String sourceContentId) {
        return jdbcTemplate.query(
                """
                SELECT id
                FROM tourism_contents
                WHERE source_content_id = ?
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
                """,
                resultSet -> resultSet.next()
                        ? Optional.of(resultSet.getLong("id"))
                        : Optional.empty(),
                sourceContentId);
    }

    public void save(Long memberId, Long contentId, LocalDateTime createdAt) {
        jdbcTemplate.update(
                """
                INSERT IGNORE INTO favorite_contents (member_id, content_id, created_at)
                VALUES (?, ?, ?)
                """,
                memberId, contentId, createdAt);
    }

    public void delete(Long memberId, String sourceContentId) {
        jdbcTemplate.update(
                """
                DELETE favorite
                FROM favorite_contents favorite
                JOIN tourism_contents content ON content.id = favorite.content_id
                WHERE favorite.member_id = ?
                  AND content.source_content_id = ?
                """,
                memberId, sourceContentId);
    }

}
