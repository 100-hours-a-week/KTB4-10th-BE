CREATE TABLE notification_occurrences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_member_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    reference_type VARCHAR(30) NOT NULL,
    reference_id VARCHAR(50) NOT NULL,
    occurrence_key VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_notification_occurrences_dedup
        UNIQUE (
            recipient_member_id,
            type,
            reference_type,
            reference_id,
            occurrence_key
        ),
    CONSTRAINT fk_notification_occurrences_recipient
        FOREIGN KEY (recipient_member_id) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '반복 실행 가능한 알림 생산자의 영속 중복 방지 기록';
