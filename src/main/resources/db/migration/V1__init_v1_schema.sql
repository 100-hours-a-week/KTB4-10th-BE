-- KGB V1 initial schema
-- MySQL 8.4 / InnoDB / utf8mb4

CREATE TABLE members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    oauth_provider VARCHAR(20) NOT NULL,
    oauth_subject VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    email VARCHAR(254) NULL,
    profile_image_url VARCHAR(2048) NULL,
    language_code VARCHAR(10) NOT NULL DEFAULT 'ko',
    status VARCHAR(20) NOT NULL DEFAULT 'ONBOARDING',
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_members_oauth UNIQUE (oauth_provider, oauth_subject),
    INDEX ix_members_status (status)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '회원, OAuth 식별 정보, 프로필과 설정';

CREATE TABLE auth_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    session_id_hash BINARY(32) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    last_used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_auth_sessions_session_id UNIQUE (session_id_hash),
    CONSTRAINT fk_auth_sessions_member FOREIGN KEY (member_id) REFERENCES members (id),
    INDEX ix_auth_sessions_member_state (member_id, revoked_at, expires_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '서버 측 로그인 세션';

CREATE TABLE member_preferences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    preference_type VARCHAR(20) NOT NULL,
    preference_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_member_preferences_selection
        UNIQUE (member_id, preference_type, preference_code),
    CONSTRAINT fk_member_preferences_member FOREIGN KEY (member_id) REFERENCES members (id),
    INDEX ix_member_preferences_code (preference_type, preference_code)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '회원이 선택한 취향 Enum 코드';

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_member_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    body VARCHAR(500) NOT NULL,
    reference_type VARCHAR(30) NULL,
    reference_id VARCHAR(50) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_recipient
        FOREIGN KEY (recipient_member_id) REFERENCES members (id),
    CONSTRAINT ck_notifications_reference_pair
        CHECK ((reference_type IS NULL) = (reference_id IS NULL)),
    INDEX ix_notifications_recipient_created (recipient_member_id, created_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '회원의 미읽은 인앱 알림';

CREATE TABLE regions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    administrative_code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_regions_administrative_code UNIQUE (administrative_code)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '17개 광역 시도 지역 기준 정보';

CREATE TABLE tourism_contents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category VARCHAR(30) NOT NULL,
    source_provider VARCHAR(30) NOT NULL,
    source_content_id VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    region_id BIGINT NOT NULL,
    address VARCHAR(500) NULL,
    location POINT SRID 4326 NOT NULL,
    phone VARCHAR(50) NULL,
    homepage_url VARCHAR(2048) NULL,
    thumbnail_url VARCHAR(2048) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_tourism_contents_source UNIQUE (source_provider, source_content_id),
    CONSTRAINT fk_tourism_contents_region FOREIGN KEY (region_id) REFERENCES regions (id),
    INDEX ix_tourism_contents_region_category (region_id, category),
    INDEX ix_tourism_contents_active_created (status, deleted_at, created_at DESC, id DESC),
    SPATIAL INDEX ix_tourism_contents_location (location)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '관광 콘텐츠 공통 원본';

CREATE TABLE event_details (
    content_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    operating_hours VARCHAR(255) NULL,
    organizer VARCHAR(200) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (content_id),
    CONSTRAINT fk_event_details_content
        FOREIGN KEY (content_id) REFERENCES tourism_contents (id),
    CONSTRAINT ck_event_details_period CHECK (start_date <= end_date)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '행사 콘텐츠 전용 상세';

CREATE TABLE content_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content_id BIGINT NOT NULL,
    image_url VARCHAR(2048) NOT NULL,
    image_url_hash BINARY(32) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    source_url VARCHAR(2048) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_content_images_url_hash UNIQUE (content_id, image_url_hash),
    CONSTRAINT fk_content_images_content
        FOREIGN KEY (content_id) REFERENCES tourism_contents (id),
    INDEX ix_content_images_content_order (content_id, sort_order)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '관광 콘텐츠 이미지';

CREATE TABLE favorite_contents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_favorite_contents_member_content UNIQUE (member_id, content_id),
    CONSTRAINT fk_favorite_contents_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_favorite_contents_content
        FOREIGN KEY (content_id) REFERENCES tourism_contents (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '회원과 관심 관광 콘텐츠의 N:M 관계';

CREATE TABLE guidebooks (
    id VARCHAR(50) NOT NULL,
    title VARCHAR(15) NOT NULL,
    region_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    companion VARCHAR(20) NOT NULL,
    people_count INT NOT NULL,
    content_html TEXT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_guidebooks_region FOREIGN KEY (region_id) REFERENCES regions (id),
    CONSTRAINT ck_guidebooks_period CHECK (start_date <= end_date),
    CONSTRAINT ck_guidebooks_people CHECK (people_count >= 1),
    CONSTRAINT ck_guidebooks_version CHECK (version >= 1)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'AI 생성 결과 가이드북';

CREATE TABLE member_guidebooks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    guidebook_id VARCHAR(50) NOT NULL,
    acquisition_type VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_member_guidebooks_member_guidebook UNIQUE (member_id, guidebook_id),
    CONSTRAINT fk_member_guidebooks_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_member_guidebooks_guidebook
        FOREIGN KEY (guidebook_id) REFERENCES guidebooks (id),
    CONSTRAINT ck_member_guidebooks_acquisition
        CHECK (acquisition_type IN ('CREATED', 'IMPORTED')),
    INDEX ix_member_guidebooks_member_created (member_id, deleted_at, created_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '회원별 가이드북 보관 및 삭제 관계';

CREATE TABLE generation_jobs (
    id VARCHAR(50) NOT NULL,
    member_id BIGINT NOT NULL,
    guidebook_id VARCHAR(50) NULL,
    job_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    request_payload JSON NOT NULL,
    error_payload JSON NULL,
    attempt_count SMALLINT NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    started_at DATETIME(6) NULL,
    attempt_started_at DATETIME(6) NULL,
    next_attempt_at DATETIME(6) NULL,
    lease_token VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    lease_expires_at DATETIME(6) NULL,
    ai_job_id VARCHAR(255) NULL,
    cancel_requested_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    active_member_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('PENDING', 'PROCESSING') THEN member_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    CONSTRAINT uq_generation_jobs_member_idempotency UNIQUE (member_id, idempotency_key),
    CONSTRAINT uq_generation_jobs_active_member UNIQUE (active_member_id),
    CONSTRAINT fk_generation_jobs_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_generation_jobs_guidebook
        FOREIGN KEY (guidebook_id) REFERENCES guidebooks (id),
    CONSTRAINT ck_generation_jobs_attempt CHECK (attempt_count BETWEEN 0 AND 3),
    INDEX ix_generation_jobs_member_state (member_id, status, created_at),
    INDEX ix_generation_jobs_guidebook_created (guidebook_id, created_at),
    INDEX ix_generation_jobs_next_attempt (status, next_attempt_at),
    INDEX ix_generation_jobs_lease (status, lease_expires_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '비동기 AI 생성 작업';

CREATE TABLE itinerary_days (
    id BIGINT NOT NULL AUTO_INCREMENT,
    guidebook_id VARCHAR(50) NOT NULL,
    day_number SMALLINT NOT NULL,
    itinerary_date DATE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_itinerary_days_number UNIQUE (guidebook_id, day_number),
    CONSTRAINT uq_itinerary_days_date UNIQUE (guidebook_id, itinerary_date),
    CONSTRAINT fk_itinerary_days_guidebook
        FOREIGN KEY (guidebook_id) REFERENCES guidebooks (id),
    CONSTRAINT ck_itinerary_days_number CHECK (day_number >= 1)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '가이드북의 여행 일자';

CREATE TABLE itinerary_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    itinerary_day_id BIGINT NOT NULL,
    tourism_content_id BIGINT NULL,
    sequence SMALLINT NOT NULL,
    scheduled_time TIME NULL,
    place_snapshot JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_itinerary_items_sequence UNIQUE (itinerary_day_id, sequence),
    CONSTRAINT fk_itinerary_items_day
        FOREIGN KEY (itinerary_day_id) REFERENCES itinerary_days (id),
    CONSTRAINT fk_itinerary_items_content
        FOREIGN KEY (tourism_content_id) REFERENCES tourism_contents (id),
    CONSTRAINT ck_itinerary_items_sequence CHECK (sequence >= 1),
    INDEX ix_itinerary_items_content (tourism_content_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '날짜별 방문 장소와 생성 시점 스냅샷';

CREATE TABLE credit_wallets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    credit_balance INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_credit_wallets_member UNIQUE (member_id),
    CONSTRAINT fk_credit_wallets_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT ck_credit_wallets_balance CHECK (credit_balance >= 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '회원별 생성권 현재 잔액';

CREATE TABLE credit_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wallet_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    credit_delta INT NOT NULL,
    credit_balance_after INT NOT NULL,
    generation_job_id VARCHAR(50) NULL,
    idempotency_key VARCHAR(150) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_credit_transactions_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_credit_transactions_wallet
        FOREIGN KEY (wallet_id) REFERENCES credit_wallets (id),
    CONSTRAINT fk_credit_transactions_job
        FOREIGN KEY (generation_job_id) REFERENCES generation_jobs (id),
    CONSTRAINT ck_credit_transactions_balance CHECK (credit_balance_after >= 0),
    INDEX ix_credit_transactions_wallet_created (wallet_id, created_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'V1 무료 생성권 증감의 불변 원장';
