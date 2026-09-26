CREATE TABLE tour_api_region_mappings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_provider VARCHAR(30) NOT NULL,
    source_region_code VARCHAR(20) NOT NULL,
    source_district_code VARCHAR(20) NOT NULL,
    region_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_tour_api_region_mappings_source
        UNIQUE (source_provider, source_region_code, source_district_code),
    CONSTRAINT fk_tour_api_region_mappings_region
        FOREIGN KEY (region_id) REFERENCES regions (id),
    INDEX ix_tour_api_region_mappings_region (region_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'TourAPI 원본 지역 코드와 서비스 2단계 지역의 매핑';

ALTER TABLE tourism_contents
    ADD COLUMN source_content_type_id VARCHAR(20) NULL AFTER source_content_id,
    ADD COLUMN source_region_code VARCHAR(20) NULL AFTER region_id,
    ADD COLUMN source_district_code VARCHAR(20) NULL AFTER source_region_code,
    ADD COLUMN classification_code_1 VARCHAR(20) NULL AFTER source_district_code,
    ADD COLUMN classification_code_2 VARCHAR(20) NULL AFTER classification_code_1,
    ADD COLUMN classification_code_3 VARCHAR(20) NULL AFTER classification_code_2,
    ADD COLUMN source_created_at DATETIME(6) NULL AFTER thumbnail_url,
    ADD COLUMN source_modified_at DATETIME(6) NULL AFTER source_created_at,
    ADD INDEX ix_tourism_contents_source_region
        (source_provider, source_region_code, source_district_code);
