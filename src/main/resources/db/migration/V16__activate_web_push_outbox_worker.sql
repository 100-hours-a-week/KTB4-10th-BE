ALTER TABLE web_push_outbox
    ADD COLUMN claim_token VARCHAR(36) NULL AFTER sent_at,
    ADD COLUMN processing_started_at DATETIME(6) NULL AFTER claim_token,
    ADD INDEX ix_web_push_outbox_processing_lease (
        status, processing_started_at, id
    );

UPDATE web_push_outbox
SET status = 'CANCELLED',
    last_error_code = 'DIRECT_DELIVERY_CUTOVER',
    claim_token = NULL,
    processing_started_at = NULL,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE status IN ('PENDING', 'RETRY_WAIT', 'PROCESSING');
