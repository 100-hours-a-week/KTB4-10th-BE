-- 기존 HTTP 작업은 메시지 없이 유지한다. publish_attempt_count는 사용자 재시도와 별개다.
ALTER TABLE generation_jobs
    ADD COLUMN message_payload JSON NULL,
    ADD COLUMN publish_attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_publish_at DATETIME(6) NULL,
    ADD COLUMN publish_claim_token VARCHAR(36) NULL,
    ADD COLUMN publish_claim_expires_at DATETIME(6) NULL,
    ADD COLUMN published_at DATETIME(6) NULL,
    ADD COLUMN last_publish_error_code VARCHAR(100) NULL,
    ADD CONSTRAINT chk_generation_jobs_publish_attempts CHECK (publish_attempt_count >= 0),
    ADD INDEX ix_generation_jobs_publish_due (status, next_publish_at, id),
    ADD INDEX ix_generation_jobs_publish_claim (status, publish_claim_expires_at);
