CREATE TABLE tour_api_sync_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id CHAR(36) NOT NULL,
    sync_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    source_contents INT NULL,
    imported_contents INT NULL,
    inactive_contents INT NULL,
    reactivated_contents INT NULL,
    source_events INT NULL,
    imported_events INT NULL,
    failure_stage VARCHAR(30) NULL,
    failure_message VARCHAR(1000) NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_tour_api_sync_runs_run_id UNIQUE (run_id),
    CONSTRAINT uq_tour_api_sync_runs_sync_date UNIQUE (sync_date),
    INDEX ix_tour_api_sync_runs_status_started (status, started_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'TourAPI 일일 동기화 실행 이력과 중복 실행 잠금';
