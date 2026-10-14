ALTER TABLE tourism_contents
    ADD COLUMN detail_synced_at DATETIME(6) NULL AFTER source_modified_at,
    ADD INDEX ix_tourism_contents_detail_bootstrap
        (source_provider, status, detail_synced_at, classification_code_1);

ALTER TABLE event_details
    ADD COLUMN organizer_phone VARCHAR(100) NULL AFTER organizer,
    ADD COLUMN event_place VARCHAR(500) NULL AFTER organizer_phone,
    ADD COLUMN program TEXT NULL AFTER event_place,
    ADD COLUMN usage_fee TEXT NULL AFTER program,
    ADD COLUMN age_limit VARCHAR(100) NULL AFTER usage_fee,
    ADD COLUMN booking_place VARCHAR(500) NULL AFTER age_limit;

CREATE TABLE event_detail_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content_id BIGINT NOT NULL,
    serial_number VARCHAR(30) NULL,
    info_name VARCHAR(200) NOT NULL,
    info_text TEXT NOT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_event_detail_items_content
        FOREIGN KEY (content_id) REFERENCES tourism_contents (id),
    INDEX ix_event_detail_items_content_order (content_id, sort_order)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'TourAPI 행사 반복 상세정보';
