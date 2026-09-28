package com.ktb10.kgb.content.repository;

import com.ktb10.kgb.content.dto.FavoriteContentItemResponse;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.dto.MapContentItemResponse.EventPeriod;
import java.sql.Date;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 관심 장소의 등록 대상 확인과 커서 목록 조회를 수행합니다. */
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

    public List<FavoriteContentRow> findAll(Long memberId, int fetchSize) {
        return jdbcTemplate.query(
                """
                SELECT
                    favorite.id AS favorite_id,
                    favorite.created_at AS favorited_at,
                    content.source_content_id,
                    content.title,
                    content.classification_code_1,
                    content.address,
                    ST_Latitude(content.location) AS latitude,
                    ST_Longitude(content.location) AS longitude,
                    content.thumbnail_url,
                    event.start_date,
                    event.end_date
                FROM favorite_contents favorite
                JOIN tourism_contents content ON content.id = favorite.content_id
                LEFT JOIN event_details event ON event.content_id = content.id
                WHERE favorite.member_id = ?
                  AND content.status = 'ACTIVE'
                  AND content.deleted_at IS NULL
                ORDER BY favorite.created_at DESC, favorite.id DESC
                LIMIT ?
                """,
                (resultSet, rowNumber) -> row(resultSet),
                memberId, fetchSize);
    }

    public List<FavoriteContentRow> findAllAfter(
            Long memberId,
            LocalDateTime createdAt,
            Long favoriteId,
            int fetchSize) {
        return jdbcTemplate.query(
                """
                SELECT
                    favorite.id AS favorite_id,
                    favorite.created_at AS favorited_at,
                    content.source_content_id,
                    content.title,
                    content.classification_code_1,
                    content.address,
                    ST_Latitude(content.location) AS latitude,
                    ST_Longitude(content.location) AS longitude,
                    content.thumbnail_url,
                    event.start_date,
                    event.end_date
                FROM favorite_contents favorite
                JOIN tourism_contents content ON content.id = favorite.content_id
                LEFT JOIN event_details event ON event.content_id = content.id
                WHERE favorite.member_id = ?
                  AND content.status = 'ACTIVE'
                  AND content.deleted_at IS NULL
                  AND (favorite.created_at < ?
                    OR (favorite.created_at = ? AND favorite.id < ?))
                ORDER BY favorite.created_at DESC, favorite.id DESC
                LIMIT ?
                """,
                (resultSet, rowNumber) -> row(resultSet),
                memberId, createdAt, createdAt, favoriteId, fetchSize);
    }

    private static FavoriteContentRow row(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        LocalDateTime favoritedAt = resultSet.getTimestamp("favorited_at")
                .toLocalDateTime();
        FavoriteContentItemResponse item = new FavoriteContentItemResponse(
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
                        resultSet.getDate("end_date")),
                favoritedAt.atOffset(ZoneOffset.UTC));
        return new FavoriteContentRow(
                resultSet.getLong("favorite_id"), favoritedAt, item);
    }

    private static EventPeriod eventPeriod(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            return null;
        }
        return new EventPeriod(startDate.toLocalDate(), endDate.toLocalDate());
    }

    public record FavoriteContentRow(
            Long favoriteId,
            LocalDateTime favoritedAt,
            FavoriteContentItemResponse item) {
    }
}
