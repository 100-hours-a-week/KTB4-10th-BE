CREATE TABLE tour_api_cache_refresh_batches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_tour_api_cache_refresh_batches_run_id UNIQUE (run_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'TourAPI 동기화별 지도 캐시 갱신 배치';

CREATE TABLE tour_api_cache_refresh_tiles (
    batch_id BIGINT NOT NULL,
    latitude_cell BIGINT NOT NULL,
    longitude_cell BIGINT NOT NULL,
    PRIMARY KEY (batch_id, latitude_cell, longitude_cell),
    CONSTRAINT fk_tour_api_cache_refresh_tiles_batch
        FOREIGN KEY (batch_id) REFERENCES tour_api_cache_refresh_batches (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'TourAPI 동기화 후 각 인스턴스가 재로드할 0.5도 타일';
