CREATE TABLE web_push_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notification_id BIGINT NOT NULL,
    recipient_member_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    last_error_code VARCHAR(100) NULL,
    sent_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_web_push_outbox_notification_subscription
        UNIQUE (notification_id, subscription_id),
    CONSTRAINT ck_web_push_outbox_status
        CHECK (status IN (
            'PENDING', 'PROCESSING', 'RETRY_WAIT',
            'SENT', 'FAILED_PERMANENT', 'CANCELLED'
        )),
    CONSTRAINT ck_web_push_outbox_attempt_count CHECK (attempt_count >= 0),
    INDEX ix_web_push_outbox_due (status, next_attempt_at, id),
    INDEX ix_web_push_outbox_subscription_status (subscription_id, status)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Web Push 발송 작업과 재처리 상태를 보관하는 Outbox';
