CREATE TABLE tour_api_content_change_occurrences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id CHAR(36) NOT NULL,
    source_provider VARCHAR(30) NOT NULL,
    source_content_id VARCHAR(100) NOT NULL,
    lifecycle_change VARCHAR(30) NOT NULL,
    changed_fields_json JSON NOT NULL,
    previous_latitude DECIMAL(10, 7) NULL,
    previous_longitude DECIMAL(10, 7) NULL,
    current_latitude DECIMAL(10, 7) NULL,
    current_longitude DECIMAL(10, 7) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_tour_api_content_change_occurrence
        UNIQUE (run_id, source_provider, source_content_id),
    CONSTRAINT fk_tour_api_content_change_occurrence_run
        FOREIGN KEY (run_id) REFERENCES tour_api_sync_runs (run_id),
    INDEX ix_tour_api_content_change_source_created (
        source_provider,
        source_content_id,
        created_at
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'TourAPI 동기화 실행별 콘텐츠 변경 occurrence';
