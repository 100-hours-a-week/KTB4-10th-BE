package com.ktb10.kgb.content.tourapi;

import com.ktb10.kgb.content.service.MapContentCacheTile;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** TourAPI 동기화로 변경된 지도 타일을 인스턴스 간 복구 가능하게 저장합니다. */
@Repository
public class TourApiCacheRefreshRepository {

    private final JdbcTemplate jdbcTemplate;

    public TourApiCacheRefreshRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public long save(UUID runId, Collection<MapContentCacheTile> tiles, Instant createdAt) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO tour_api_cache_refresh_batches (run_id, created_at) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, runId.toString());
            statement.setTimestamp(2, Timestamp.from(createdAt));
            return statement;
        }, keyHolder);
        long batchId = keyHolder.getKey().longValue();
        if (!tiles.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    """
                    INSERT INTO tour_api_cache_refresh_tiles (
                        batch_id, latitude_cell, longitude_cell
                    ) VALUES (?, ?, ?)
                    """,
                    tiles,
                    tiles.size(),
                    (statement, tile) -> {
                        statement.setLong(1, batchId);
                        statement.setLong(2, tile.latitudeCell());
                        statement.setLong(3, tile.longitudeCell());
                    });
        }
        return batchId;
    }

    public long findLatestBatchId() {
        Long result = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(MAX(id), 0)
                FROM tour_api_cache_refresh_batches
                """,
                Long.class);
        return result == null ? 0L : result;
    }

    public List<MapContentCacheTile> findTilesAfter(long afterBatchId, long throughBatchId) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT tile.latitude_cell, tile.longitude_cell
                FROM tour_api_cache_refresh_tiles tile
                JOIN tour_api_cache_refresh_batches batch ON batch.id = tile.batch_id
                WHERE batch.id > ?
                  AND batch.id <= ?
                ORDER BY tile.latitude_cell, tile.longitude_cell
                """,
                (resultSet, rowNum) -> new MapContentCacheTile(
                        resultSet.getLong("latitude_cell"),
                        resultSet.getLong("longitude_cell")),
                afterBatchId,
                throughBatchId);
    }
}
