package com.ktb10.kgb.content.repository;

import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** 지도 핀의 관심 장소 등록 대상 확인과 저장·삭제를 수행합니다. */
@Repository
public class FavoriteContentQuery {

    private static final String TOUR_API_PROVIDER = "TOUR_API";

    private static final RowMapper<FavoriteContentListRow> LIST_ROW_MAPPER =
            (resultSet, rowNumber) -> new FavoriteContentListRow(
                    resultSet.getLong("favorite_id"),
                    resultSet.getObject("favorite_created_at", LocalDateTime.class),
                    resultSet.getString("source_content_id"),
                    resultSet.getString("title"),
                    "EV".equals(resultSet.getString("classification_code_1"))
                            ? ContentType.EVENT
                            : ContentType.PLACE,
                    resultSet.getString("address"),
                    resultSet.getString("thumbnail_url"),
                    localDate(resultSet.getDate("event_start_date")),
                    localDate(resultSet.getDate("event_end_date")));

    private final JdbcTemplate jdbcTemplate;

    public FavoriteContentQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> findActiveContentId(String sourceContentId) {
        return jdbcTemplate.query(
                """
                SELECT id
                FROM tourism_contents
                WHERE source_provider = ?
                  AND source_content_id = ?
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
                """,
                resultSet -> resultSet.next()
                        ? Optional.of(resultSet.getLong("id"))
                        : Optional.empty(),
                TOUR_API_PROVIDER,
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
                  AND content.source_provider = ?
                  AND content.source_content_id = ?
                """,
                memberId,
                TOUR_API_PROVIDER,
                sourceContentId);
    }

    public List<FavoriteContentListRow> findPage(Long memberId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT favorite.id AS favorite_id,
                       favorite.created_at AS favorite_created_at,
                       content.source_content_id,
                       content.title,
                       content.classification_code_1,
                       content.address,
                       content.thumbnail_url,
                       event.start_date AS event_start_date,
                       event.end_date AS event_end_date
                FROM favorite_contents favorite
                JOIN tourism_contents content ON content.id = favorite.content_id
                LEFT JOIN event_details event ON event.content_id = content.id
                WHERE favorite.member_id = ?
                  AND content.status = 'ACTIVE'
                  AND content.deleted_at IS NULL
                ORDER BY favorite.created_at DESC, favorite.id DESC
                LIMIT ?
                """,
                LIST_ROW_MAPPER,
                memberId,
                limit);
    }

    public List<FavoriteContentListRow> findPageAfter(
            Long memberId,
            LocalDateTime createdAt,
            Long favoriteId,
            int limit) {
        return jdbcTemplate.query(
                """
                SELECT favorite.id AS favorite_id,
                       favorite.created_at AS favorite_created_at,
                       content.source_content_id,
                       content.title,
                       content.classification_code_1,
                       content.address,
                       content.thumbnail_url,
                       event.start_date AS event_start_date,
                       event.end_date AS event_end_date
                FROM favorite_contents favorite
                JOIN tourism_contents content ON content.id = favorite.content_id
                LEFT JOIN event_details event ON event.content_id = content.id
                WHERE favorite.member_id = ?
                  AND content.status = 'ACTIVE'
                  AND content.deleted_at IS NULL
                  AND (
                      favorite.created_at < ?
                      OR (favorite.created_at = ? AND favorite.id < ?)
                  )
                ORDER BY favorite.created_at DESC, favorite.id DESC
                LIMIT ?
                """,
                LIST_ROW_MAPPER,
                memberId,
                createdAt,
                createdAt,
                favoriteId,
                limit);
    }

    public boolean exists(Long memberId, Long contentId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM favorite_contents
                WHERE member_id = ?
                  AND content_id = ?
                """,
                Integer.class,
                memberId,
                contentId);
        return count != null && count > 0;
    }

    private static LocalDate localDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    public record FavoriteContentListRow(
            Long favoriteId,
            LocalDateTime favoriteCreatedAt,
            String contentId,
            String title,
            ContentType contentType,
            String address,
            String thumbnailUrl,
            LocalDate eventStartDate,
            LocalDate eventEndDate) {
    }

}
