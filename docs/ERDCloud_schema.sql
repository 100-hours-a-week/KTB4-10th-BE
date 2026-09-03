-- V3 ERDCloud import schema
-- Dialect: MySQL 8.x (ERDCloud import compatibility)
-- Source of truth: docs/테이블정의서.md
-- Production DB is PostgreSQL. See the notes at the end for PostgreSQL-only constraints.

CREATE TABLE members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    oauth_provider VARCHAR(20) NOT NULL,
    oauth_subject VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    profile_image_url TEXT NULL,
    language_code VARCHAR(10) NOT NULL DEFAULT 'ko',
    status VARCHAR(20) NOT NULL DEFAULT 'ONBOARDING',
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    withdrawn_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_members_oauth UNIQUE (oauth_provider, oauth_subject),
    INDEX ix_members_status (status)
) COMMENT = '회원, OAuth 식별 정보, 프로필과 설정';

CREATE TABLE auth_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    last_used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_auth_sessions_refresh_token UNIQUE (refresh_token_hash),
    CONSTRAINT fk_auth_sessions_member FOREIGN KEY (member_id) REFERENCES members (id),
    INDEX ix_auth_sessions_member_state (member_id, revoked_at, expires_at)
) COMMENT = '리프레시 토큰 단위 로그인 세션';

CREATE TABLE member_preferences (
    member_id BIGINT NOT NULL,
    preference_type VARCHAR(20) NOT NULL,
    preference_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (member_id, preference_type, preference_code),
    CONSTRAINT fk_member_preferences_member FOREIGN KEY (member_id) REFERENCES members (id),
    INDEX ix_member_preferences_code (preference_type, preference_code)
) COMMENT = '회원이 선택한 취향 Enum 코드';

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
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_member_id) REFERENCES members (id),
    CONSTRAINT ck_notifications_reference_pair CHECK ((reference_type IS NULL) = (reference_id IS NULL)),
    INDEX ix_notifications_recipient_created (recipient_member_id, created_at)
) COMMENT = '회원의 미읽은 인앱 알림';

CREATE TABLE regions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    administrative_code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_regions_administrative_code UNIQUE (administrative_code)
) COMMENT = '17개 광역 시도 지역 기준 정보';

CREATE TABLE tourism_contents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category VARCHAR(30) NOT NULL,
    source_provider VARCHAR(30) NOT NULL,
    source_content_id VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    region_id BIGINT NOT NULL,
    address VARCHAR(500) NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    phone VARCHAR(50) NULL,
    homepage_url TEXT NULL,
    thumbnail_url TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_tourism_contents_source UNIQUE (source_provider, source_content_id),
    CONSTRAINT fk_tourism_contents_region FOREIGN KEY (region_id) REFERENCES regions (id),
    INDEX ix_tourism_contents_region_category (region_id, category)
) COMMENT = '관광 콘텐츠 공통 원본';

CREATE TABLE event_details (
    content_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    operating_hours VARCHAR(255) NULL,
    organizer VARCHAR(200) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (content_id),
    CONSTRAINT fk_event_details_content FOREIGN KEY (content_id) REFERENCES tourism_contents (id),
    CONSTRAINT ck_event_details_period CHECK (start_date <= end_date)
) COMMENT = '행사 콘텐츠 전용 상세';

CREATE TABLE content_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content_id BIGINT NOT NULL,
    image_url TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    source_url TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_content_images_content FOREIGN KEY (content_id) REFERENCES tourism_contents (id),
    INDEX ix_content_images_content_order (content_id, sort_order)
) COMMENT = '관광 콘텐츠 이미지';

CREATE TABLE favorite_contents (
    member_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (member_id, content_id),
    CONSTRAINT fk_favorite_contents_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_favorite_contents_content FOREIGN KEY (content_id) REFERENCES tourism_contents (id)
) COMMENT = '회원과 관심 관광 콘텐츠의 N:M 관계';

CREATE TABLE guidebooks (
    id VARCHAR(50) NOT NULL,
    owner_member_id BIGINT NOT NULL,
    origin_guidebook_id VARCHAR(50) NULL,
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
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_guidebooks_owner FOREIGN KEY (owner_member_id) REFERENCES members (id),
    CONSTRAINT fk_guidebooks_origin FOREIGN KEY (origin_guidebook_id) REFERENCES guidebooks (id),
    CONSTRAINT fk_guidebooks_region FOREIGN KEY (region_id) REFERENCES regions (id),
    CONSTRAINT ck_guidebooks_period CHECK (start_date <= end_date),
    CONSTRAINT ck_guidebooks_people CHECK (people_count >= 1),
    CONSTRAINT ck_guidebooks_version CHECK (version >= 1),
    INDEX ix_guidebooks_owner_created (owner_member_id, created_at)
) COMMENT = '성공한 AI 가이드북의 현재 버전';

CREATE TABLE generation_jobs (
    id VARCHAR(50) NOT NULL,
    member_id BIGINT NOT NULL,
    guidebook_id VARCHAR(50) NULL,
    job_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    request_payload JSON NOT NULL,
    error_payload JSON NULL,
    attempt_count SMALLINT NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(100) NOT NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_generation_jobs_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_generation_jobs_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_generation_jobs_guidebook FOREIGN KEY (guidebook_id) REFERENCES guidebooks (id),
    CONSTRAINT ck_generation_jobs_attempt CHECK (attempt_count BETWEEN 0 AND 3),
    INDEX ix_generation_jobs_member_state (member_id, status, created_at),
    INDEX ix_generation_jobs_guidebook_created (guidebook_id, created_at)
) COMMENT = '최초 생성과 재생성의 비동기 AI 작업';

CREATE TABLE itinerary_days (
    id BIGINT NOT NULL AUTO_INCREMENT,
    guidebook_id VARCHAR(50) NOT NULL,
    day_number SMALLINT NOT NULL,
    itinerary_date DATE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_itinerary_days_number UNIQUE (guidebook_id, day_number),
    CONSTRAINT uq_itinerary_days_date UNIQUE (guidebook_id, itinerary_date),
    CONSTRAINT fk_itinerary_days_guidebook FOREIGN KEY (guidebook_id) REFERENCES guidebooks (id),
    CONSTRAINT ck_itinerary_days_number CHECK (day_number >= 1)
) COMMENT = '가이드북의 여행 일자';

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
    CONSTRAINT fk_itinerary_items_day FOREIGN KEY (itinerary_day_id) REFERENCES itinerary_days (id),
    CONSTRAINT fk_itinerary_items_content FOREIGN KEY (tourism_content_id) REFERENCES tourism_contents (id),
    CONSTRAINT ck_itinerary_items_sequence CHECK (sequence >= 1),
    INDEX ix_itinerary_items_content (tourism_content_id)
) COMMENT = '날짜별 방문 장소와 생성 시점 스냅샷';

CREATE TABLE share_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    guidebook_id VARCHAR(50) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_share_links_token UNIQUE (token_hash),
    CONSTRAINT fk_share_links_guidebook FOREIGN KEY (guidebook_id) REFERENCES guidebooks (id)
) COMMENT = '가이드북 공유 토큰과 만료 정보';

CREATE TABLE guidebook_imports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    share_link_id BIGINT NOT NULL,
    imported_by_member_id BIGINT NOT NULL,
    root_guidebook_id VARCHAR(50) NOT NULL,
    imported_guidebook_id VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_guidebook_imports_member_root UNIQUE (imported_by_member_id, root_guidebook_id),
    CONSTRAINT uq_guidebook_imports_copy UNIQUE (imported_guidebook_id),
    CONSTRAINT fk_guidebook_imports_share FOREIGN KEY (share_link_id) REFERENCES share_links (id),
    CONSTRAINT fk_guidebook_imports_member FOREIGN KEY (imported_by_member_id) REFERENCES members (id),
    CONSTRAINT fk_guidebook_imports_root FOREIGN KEY (root_guidebook_id) REFERENCES guidebooks (id),
    CONSTRAINT fk_guidebook_imports_copy FOREIGN KEY (imported_guidebook_id) REFERENCES guidebooks (id)
) COMMENT = '최초 원본과 회원 기준 가이드북 가져오기';

CREATE TABLE guidebook_evaluations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    guidebook_id VARCHAR(50) NOT NULL,
    member_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    prompt_dismissed_at DATETIME(6) NULL,
    submitted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_guidebook_evaluations_target UNIQUE (guidebook_id, member_id),
    CONSTRAINT fk_guidebook_evaluations_guidebook FOREIGN KEY (guidebook_id) REFERENCES guidebooks (id),
    CONSTRAINT fk_guidebook_evaluations_member FOREIGN KEY (member_id) REFERENCES members (id),
    INDEX ix_guidebook_evaluations_member_state (member_id, status)
) COMMENT = '가이드북 단위 평가 안내와 제출 상태';

CREATE TABLE place_ratings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    tourism_content_id BIGINT NOT NULL,
    score SMALLINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_place_ratings_member_content UNIQUE (member_id, tourism_content_id),
    CONSTRAINT fk_place_ratings_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_place_ratings_content FOREIGN KEY (tourism_content_id) REFERENCES tourism_contents (id),
    CONSTRAINT ck_place_ratings_score CHECK (score IS NULL OR score BETWEEN 0 AND 5),
    INDEX ix_place_ratings_content_created (tourism_content_id, created_at)
) COMMENT = '회원과 관광 콘텐츠별 최신 정수 별점';

CREATE TABLE ranking_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    period_type VARCHAR(20) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    region_id BIGINT NULL,
    calculated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ranking_snapshots_region FOREIGN KEY (region_id) REFERENCES regions (id),
    CONSTRAINT ck_ranking_snapshots_period CHECK (period_start <= period_end)
) COMMENT = '일간·주간·월간, 전국·지역별 랭킹 스냅샷';

CREATE TABLE ranking_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ranking_snapshot_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    rank_position INT NOT NULL,
    weighted_score DECIMAL(8,5) NOT NULL,
    raw_average DECIMAL(3,2) NOT NULL,
    rating_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_ranking_entries_content UNIQUE (ranking_snapshot_id, content_id),
    CONSTRAINT uq_ranking_entries_position UNIQUE (ranking_snapshot_id, rank_position),
    CONSTRAINT fk_ranking_entries_snapshot FOREIGN KEY (ranking_snapshot_id) REFERENCES ranking_snapshots (id),
    CONSTRAINT fk_ranking_entries_content FOREIGN KEY (content_id) REFERENCES tourism_contents (id),
    CONSTRAINT ck_ranking_entries_position CHECK (rank_position >= 1),
    CONSTRAINT ck_ranking_entries_count CHECK (rating_count >= 0)
) COMMENT = '랭킹 스냅샷별 콘텐츠 순위와 계산 결과';

CREATE TABLE credit_products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    credit_amount INT NOT NULL,
    price BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'KRW',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_credit_products_amount CHECK (credit_amount > 0),
    CONSTRAINT ck_credit_products_price CHECK (price >= 0)
) COMMENT = '판매 가능한 생성권 상품';

CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    merchant_order_id VARCHAR(100) NOT NULL,
    member_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    ordered_credit_amount INT NOT NULL,
    total_amount BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    idempotency_key VARCHAR(100) NOT NULL,
    paid_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_orders_merchant_order UNIQUE (merchant_order_id),
    CONSTRAINT uq_orders_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_orders_product FOREIGN KEY (product_id) REFERENCES credit_products (id),
    CONSTRAINT ck_orders_credit_amount CHECK (ordered_credit_amount > 0),
    CONSTRAINT ck_orders_total_amount CHECK (total_amount >= 0),
    INDEX ix_orders_member_created (member_id, created_at)
) COMMENT = '생성권 상품 구매 주문과 주문 시점 스냅샷';

CREATE TABLE payment_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    pg_provider VARCHAR(30) NOT NULL,
    pg_transaction_id VARCHAR(150) NULL,
    status VARCHAR(20) NOT NULL,
    requested_amount BIGINT NOT NULL,
    approved_amount BIGINT NULL,
    failure_code VARCHAR(100) NULL,
    failure_message VARCHAR(500) NULL,
    requested_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6) NULL,
    failed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_payment_attempts_transaction UNIQUE (pg_provider, pg_transaction_id),
    CONSTRAINT fk_payment_attempts_order FOREIGN KEY (order_id) REFERENCES orders (id),
    INDEX ix_payment_attempts_order_created (order_id, created_at)
) COMMENT = '주문별 PG 결제 시도와 검증 결과';

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
) COMMENT = '회원별 생성권 현재 잔액';

CREATE TABLE credit_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wallet_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    credit_delta INT NOT NULL,
    credit_balance_after INT NOT NULL,
    order_id BIGINT NULL,
    generation_job_id VARCHAR(50) NULL,
    idempotency_key VARCHAR(150) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_credit_transactions_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_credit_transactions_wallet FOREIGN KEY (wallet_id) REFERENCES credit_wallets (id),
    CONSTRAINT fk_credit_transactions_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_credit_transactions_job FOREIGN KEY (generation_job_id) REFERENCES generation_jobs (id),
    CONSTRAINT ck_credit_transactions_balance CHECK (credit_balance_after >= 0),
    INDEX ix_credit_transactions_wallet_created (wallet_id, created_at)
) COMMENT = '생성권 증감의 불변 원장';

-- PostgreSQL-only production constraints not representable as ordinary ERDCloud keys:
-- 1) One active generation job per member:
--    CREATE UNIQUE INDEX uq_generation_jobs_active_member
--    ON generation_jobs(member_id)
--    WHERE status IN ('PENDING', 'PROCESSING');
-- 2) One ranking snapshot per period and scope:
--    CREATE UNIQUE INDEX uq_ranking_snapshot_region
--    ON ranking_snapshots(period_type, period_start, period_end, region_id)
--    WHERE region_id IS NOT NULL;
--    CREATE UNIQUE INDEX uq_ranking_snapshot_all
--    ON ranking_snapshots(period_type, period_start, period_end)
--    WHERE region_id IS NULL;
-- 3) PostgreSQL uses TIMESTAMPTZ instead of DATETIME(6), JSONB instead of JSON,
--    and GENERATED BY DEFAULT AS IDENTITY instead of AUTO_INCREMENT.
-- 4) content_images requires UNIQUE (content_id, image_url) in PostgreSQL.
--    It is omitted here because MySQL cannot create a full UNIQUE key on TEXT.
