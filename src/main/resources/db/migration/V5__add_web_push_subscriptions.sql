CREATE TABLE web_push_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    auth_session_id BIGINT NOT NULL,
    endpoint TEXT CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    endpoint_hash BINARY(32) NOT NULL,
    p256dh VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    auth_secret VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expiration_at DATETIME(6) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failure_count INT NOT NULL DEFAULT 0,
    last_success_at DATETIME(6) NULL,
    last_failure_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_web_push_subscriptions_endpoint_hash UNIQUE (endpoint_hash),
    CONSTRAINT fk_web_push_subscriptions_member
        FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_web_push_subscriptions_auth_session
        FOREIGN KEY (auth_session_id) REFERENCES auth_sessions (id),
    CONSTRAINT ck_web_push_subscriptions_status
        CHECK (status IN ('ACTIVE', 'INVALID', 'REVOKED')),
    CONSTRAINT ck_web_push_subscriptions_failure_count CHECK (failure_count >= 0),
    INDEX ix_web_push_subscriptions_member_status (member_id, status),
    INDEX ix_web_push_subscriptions_session_status (auth_session_id, status)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '회원 브라우저별 표준 Web Push 구독';
