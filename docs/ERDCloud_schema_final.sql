-- KGB final schema snapshot / MySQL 8 / Flyway V20 / dev 277bd55
-- Schema only. Use an empty schema for importing.

SET @previous_foreign_key_checks = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `content_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content_id` bigint NOT NULL,
  `image_url` varchar(2048) NOT NULL,
  `image_url_hash` binary(32) NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `source_url` varchar(2048) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_content_images_url_hash` (`content_id`,`image_url_hash`),
  KEY `ix_content_images_content_order` (`content_id`,`sort_order`),
  CONSTRAINT `fk_content_images_content` FOREIGN KEY (`content_id`) REFERENCES `tourism_contents` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관광 콘텐츠 이미지';

CREATE TABLE `credit_transactions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `wallet_id` bigint NOT NULL,
  `type` varchar(30) NOT NULL,
  `credit_delta` int NOT NULL,
  `credit_balance_after` int NOT NULL,
  `generation_job_id` bigint DEFAULT NULL,
  `idempotency_key` varchar(150) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_credit_transactions_idempotency` (`idempotency_key`),
  KEY `fk_credit_transactions_job` (`generation_job_id`),
  KEY `ix_credit_transactions_wallet_created` (`wallet_id`,`created_at`),
  CONSTRAINT `fk_credit_transactions_job` FOREIGN KEY (`generation_job_id`) REFERENCES `generation_jobs` (`id`),
  CONSTRAINT `fk_credit_transactions_wallet` FOREIGN KEY (`wallet_id`) REFERENCES `credit_wallets` (`id`),
  CONSTRAINT `ck_credit_transactions_balance` CHECK ((`credit_balance_after` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='V1 무료 생성권 증감의 불변 원장';

CREATE TABLE `credit_wallets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `credit_balance` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_credit_wallets_member` (`member_id`),
  CONSTRAINT `fk_credit_wallets_member` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
  CONSTRAINT `ck_credit_wallets_balance` CHECK ((`credit_balance` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원별 생성권 현재 잔액';

CREATE TABLE `event_details` (
  `content_id` bigint NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `operating_hours` varchar(255) DEFAULT NULL,
  `organizer` varchar(200) DEFAULT NULL,
  `organizer_phone` varchar(100) DEFAULT NULL,
  `event_place` varchar(500) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`content_id`),
  CONSTRAINT `fk_event_details_content` FOREIGN KEY (`content_id`) REFERENCES `tourism_contents` (`id`),
  CONSTRAINT `ck_event_details_period` CHECK ((`start_date` <= `end_date`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='행사 콘텐츠 전용 상세';

CREATE TABLE `favorite_contents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `content_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_favorite_contents_member_content` (`member_id`,`content_id`),
  KEY `fk_favorite_contents_content` (`content_id`),
  KEY `ix_favorite_contents_member_created` (`member_id`,`created_at` DESC,`id` DESC),
  CONSTRAINT `fk_favorite_contents_content` FOREIGN KEY (`content_id`) REFERENCES `tourism_contents` (`id`),
  CONSTRAINT `fk_favorite_contents_member` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원과 관심 관광 콘텐츠의 N:M 관계';

CREATE TABLE `generation_jobs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `guidebook_id` bigint DEFAULT NULL,
  `job_type` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `request_payload` json NOT NULL,
  `error_payload` json DEFAULT NULL,
  `attempt_count` smallint NOT NULL DEFAULT '0',
  `idempotency_key` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `attempt_started_at` datetime(6) DEFAULT NULL,
  `lease_token` varchar(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `lease_version` bigint unsigned NOT NULL DEFAULT '0',
  `lease_expires_at` datetime(6) DEFAULT NULL,
  `ai_job_id` varchar(255) DEFAULT NULL,
  `cancel_requested_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active_member_id` bigint GENERATED ALWAYS AS ((case when (`status` in (_utf8mb4'PENDING',_utf8mb4'PUBLISH_PENDING',_utf8mb4'QUEUED',_utf8mb4'PROCESSING')) then `member_id` else NULL end)) STORED,
  `message_payload` json DEFAULT NULL,
  `publish_attempt_count` int NOT NULL DEFAULT '0',
  `next_publish_at` datetime(6) DEFAULT NULL,
  `publish_claim_token` varchar(36) DEFAULT NULL,
  `publish_claim_expires_at` datetime(6) DEFAULT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  `last_publish_error_code` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_generation_jobs_member_idempotency` (`member_id`,`idempotency_key`),
  UNIQUE KEY `uq_generation_jobs_active_member` (`active_member_id`),
  UNIQUE KEY `uq_generation_jobs_ai_job_id` (`ai_job_id`),
  KEY `ix_generation_jobs_member_state` (`member_id`,`status`,`created_at`),
  KEY `ix_generation_jobs_guidebook_created` (`guidebook_id`,`created_at`),
  KEY `ix_generation_jobs_lease` (`status`,`lease_expires_at`),
  KEY `ix_generation_jobs_publish_due` (`status`,`next_publish_at`,`id`),
  KEY `ix_generation_jobs_publish_claim` (`status`,`publish_claim_expires_at`),
  CONSTRAINT `fk_generation_jobs_guidebook` FOREIGN KEY (`guidebook_id`) REFERENCES `guidebooks` (`id`),
  CONSTRAINT `fk_generation_jobs_member` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
  CONSTRAINT `chk_generation_jobs_publish_attempts` CHECK ((`publish_attempt_count` >= 0)),
  CONSTRAINT `ck_generation_jobs_attempt` CHECK ((`attempt_count` between 0 and 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='비동기 AI 생성 작업';

CREATE TABLE `guidebooks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(15) NOT NULL,
  `region_id` bigint NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `companion` varchar(20) NOT NULL,
  `people_count` int NOT NULL,
  `thumbnail_url` varchar(2048) DEFAULT NULL,
  `content_html` text,
  `version` int NOT NULL DEFAULT '1',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_guidebooks_region` (`region_id`),
  CONSTRAINT `fk_guidebooks_region` FOREIGN KEY (`region_id`) REFERENCES `regions` (`id`),
  CONSTRAINT `ck_guidebooks_people` CHECK ((`people_count` >= 1)),
  CONSTRAINT `ck_guidebooks_period` CHECK ((`start_date` <= `end_date`)),
  CONSTRAINT `ck_guidebooks_version` CHECK ((`version` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 생성 결과 가이드북';

CREATE TABLE `itinerary_days` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `guidebook_id` bigint NOT NULL,
  `day_number` smallint NOT NULL,
  `itinerary_date` date NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_itinerary_days_number` (`guidebook_id`,`day_number`),
  UNIQUE KEY `uq_itinerary_days_date` (`guidebook_id`,`itinerary_date`),
  CONSTRAINT `fk_itinerary_days_guidebook` FOREIGN KEY (`guidebook_id`) REFERENCES `guidebooks` (`id`),
  CONSTRAINT `ck_itinerary_days_number` CHECK ((`day_number` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='가이드북의 여행 일자';

CREATE TABLE `itinerary_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `itinerary_day_id` bigint NOT NULL,
  `tourism_content_id` bigint DEFAULT NULL,
  `sequence` smallint NOT NULL,
  `scheduled_time` time DEFAULT NULL,
  `place_snapshot` json NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_itinerary_items_sequence` (`itinerary_day_id`,`sequence`),
  KEY `ix_itinerary_items_content` (`tourism_content_id`),
  CONSTRAINT `fk_itinerary_items_content` FOREIGN KEY (`tourism_content_id`) REFERENCES `tourism_contents` (`id`),
  CONSTRAINT `fk_itinerary_items_day` FOREIGN KEY (`itinerary_day_id`) REFERENCES `itinerary_days` (`id`),
  CONSTRAINT `ck_itinerary_items_sequence` CHECK ((`sequence` between 1 and 20))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='날짜별 방문 장소와 생성 시점 스냅샷';

CREATE TABLE `member_guidebooks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `guidebook_id` bigint NOT NULL,
  `acquisition_type` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_member_guidebooks_member_guidebook` (`member_id`,`guidebook_id`),
  KEY `fk_member_guidebooks_guidebook` (`guidebook_id`),
  KEY `ix_member_guidebooks_member_created` (`member_id`,`deleted_at`,`created_at`),
  CONSTRAINT `fk_member_guidebooks_guidebook` FOREIGN KEY (`guidebook_id`) REFERENCES `guidebooks` (`id`),
  CONSTRAINT `fk_member_guidebooks_member` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
  CONSTRAINT `ck_member_guidebooks_acquisition` CHECK ((`acquisition_type` in (_utf8mb4'CREATED',_utf8mb4'IMPORTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원별 가이드북 보관 및 삭제 관계';

CREATE TABLE `member_preferences` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `preference_type` varchar(20) NOT NULL,
  `preference_code` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_member_preferences_selection` (`member_id`,`preference_type`,`preference_code`),
  KEY `ix_member_preferences_code` (`preference_type`,`preference_code`),
  CONSTRAINT `fk_member_preferences_member` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원이 선택한 취향 Enum 코드';

CREATE TABLE `members` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `oauth_provider` varchar(20) NOT NULL,
  `oauth_subject` varchar(255) NOT NULL,
  `nickname` varchar(50) NOT NULL,
  `email` varchar(254) DEFAULT NULL,
  `profile_image_url` varchar(2048) DEFAULT NULL,
  `language_code` varchar(10) NOT NULL DEFAULT 'ko',
  `status` varchar(20) NOT NULL DEFAULT 'ONBOARDING',
  `push_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `deleted_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_members_oauth` (`oauth_provider`,`oauth_subject`),
  KEY `ix_members_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원, OAuth 식별 정보, 프로필과 설정';

CREATE TABLE `notification_occurrences` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recipient_member_id` bigint NOT NULL,
  `type` varchar(30) NOT NULL,
  `reference_type` varchar(30) NOT NULL,
  `reference_id` varchar(50) NOT NULL,
  `occurrence_key` varchar(50) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_occurrences_dedup` (`recipient_member_id`,`type`,`reference_type`,`reference_id`,`occurrence_key`),
  CONSTRAINT `fk_notification_occurrences_recipient` FOREIGN KEY (`recipient_member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='반복 실행 가능한 알림 생산자의 영속 중복 방지 기록';

CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recipient_member_id` bigint NOT NULL,
  `type` varchar(30) NOT NULL,
  `title` varchar(100) NOT NULL,
  `body` varchar(500) NOT NULL,
  `reference_type` varchar(30) DEFAULT NULL,
  `reference_id` varchar(50) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_notifications_recipient_created` (`recipient_member_id`,`created_at`),
  CONSTRAINT `fk_notifications_recipient` FOREIGN KEY (`recipient_member_id`) REFERENCES `members` (`id`),
  CONSTRAINT `ck_notifications_reference_pair` CHECK (((`reference_type` is null) = (`reference_id` is null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원의 미읽은 인앱 알림';

CREATE TABLE `regions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `administrative_code` varchar(20) NOT NULL,
  `name` varchar(100) NOT NULL,
  `region_level` varchar(20) NOT NULL DEFAULT 'PROVINCE',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_regions_administrative_code` (`administrative_code`),
  KEY `ix_regions_parent_name` (`parent_id`,`name`),
  CONSTRAINT `fk_regions_parent` FOREIGN KEY (`parent_id`) REFERENCES `regions` (`id`),
  CONSTRAINT `ck_regions_hierarchy` CHECK ((((`region_level` = _utf8mb4'PROVINCE') and (`parent_id` is null)) or ((`region_level` = _utf8mb4'DISTRICT') and (`parent_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='17개 광역 시도 지역 기준 정보';

CREATE TABLE `tour_api_cache_refresh_batches` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` char(36) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_tour_api_cache_refresh_batches_run_id` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TourAPI 동기화별 지도 캐시 갱신 배치';

CREATE TABLE `tour_api_cache_refresh_tiles` (
  `batch_id` bigint NOT NULL,
  `latitude_cell` bigint NOT NULL,
  `longitude_cell` bigint NOT NULL,
  PRIMARY KEY (`batch_id`,`latitude_cell`,`longitude_cell`),
  CONSTRAINT `fk_tour_api_cache_refresh_tiles_batch` FOREIGN KEY (`batch_id`) REFERENCES `tour_api_cache_refresh_batches` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TourAPI 동기화 후 각 인스턴스가 재로드할 0.5도 타일';

CREATE TABLE `tour_api_content_change_occurrences` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` char(36) NOT NULL,
  `source_provider` varchar(30) NOT NULL,
  `source_content_id` varchar(100) NOT NULL,
  `lifecycle_change` varchar(30) NOT NULL,
  `changed_fields_json` json NOT NULL,
  `previous_latitude` decimal(10,7) DEFAULT NULL,
  `previous_longitude` decimal(10,7) DEFAULT NULL,
  `current_latitude` decimal(10,7) DEFAULT NULL,
  `current_longitude` decimal(10,7) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_tour_api_content_change_occurrence` (`run_id`,`source_provider`,`source_content_id`),
  KEY `ix_tour_api_content_change_source_created` (`source_provider`,`source_content_id`,`created_at`),
  CONSTRAINT `fk_tour_api_content_change_occurrence_run` FOREIGN KEY (`run_id`) REFERENCES `tour_api_sync_runs` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TourAPI 동기화 실행별 콘텐츠 변경 occurrence';

CREATE TABLE `tour_api_region_mappings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_provider` varchar(30) NOT NULL,
  `source_region_code` varchar(20) NOT NULL,
  `source_district_code` varchar(20) NOT NULL,
  `region_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_tour_api_region_mappings_source` (`source_provider`,`source_region_code`,`source_district_code`),
  KEY `ix_tour_api_region_mappings_region` (`region_id`),
  CONSTRAINT `fk_tour_api_region_mappings_region` FOREIGN KEY (`region_id`) REFERENCES `regions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TourAPI 원본 지역 코드와 서비스 2단계 지역의 매핑';

CREATE TABLE `tour_api_sync_runs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` char(36) NOT NULL,
  `sync_date` date NOT NULL,
  `status` varchar(20) NOT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `source_contents` int DEFAULT NULL,
  `imported_contents` int DEFAULT NULL,
  `inactive_contents` int DEFAULT NULL,
  `reactivated_contents` int DEFAULT NULL,
  `source_events` int DEFAULT NULL,
  `imported_events` int DEFAULT NULL,
  `failure_stage` varchar(30) DEFAULT NULL,
  `failure_message` varchar(1000) DEFAULT NULL,
  `started_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_tour_api_sync_runs_run_id` (`run_id`),
  UNIQUE KEY `uq_tour_api_sync_runs_sync_date` (`sync_date`),
  KEY `ix_tour_api_sync_runs_status_started` (`status`,`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TourAPI 일일 동기화 실행 이력과 중복 실행 잠금';

CREATE TABLE `tourism_contents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_provider` varchar(30) NOT NULL,
  `source_content_id` varchar(100) NOT NULL,
  `source_content_type_id` varchar(20) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `description` text,
  `region_id` bigint NOT NULL,
  `source_region_code` varchar(20) DEFAULT NULL,
  `source_district_code` varchar(20) DEFAULT NULL,
  `classification_code_1` varchar(20) DEFAULT NULL,
  `classification_code_2` varchar(20) DEFAULT NULL,
  `classification_code_3` varchar(20) DEFAULT NULL,
  `address` varchar(500) DEFAULT NULL,
  `location` point NOT NULL /*!80003 SRID 4326 */,
  `phone` varchar(50) DEFAULT NULL,
  `homepage_url` varchar(2048) DEFAULT NULL,
  `thumbnail_url` varchar(2048) DEFAULT NULL,
  `source_created_at` datetime(6) DEFAULT NULL,
  `source_modified_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `deleted_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_tourism_contents_source` (`source_provider`,`source_content_id`),
  KEY `ix_tourism_contents_active_created` (`status`,`deleted_at`,`created_at` DESC,`id` DESC),
  SPATIAL KEY `ix_tourism_contents_location` (`location`),
  KEY `ix_tourism_contents_source_region` (`source_provider`,`source_region_code`,`source_district_code`),
  KEY `ix_tourism_contents_region_classification_1` (`region_id`,`classification_code_1`),
  KEY `idx_tourism_contents_new_event_lookup` (`classification_code_1`,`status`,`deleted_at`,`source_created_at`,`id`),
  CONSTRAINT `fk_tourism_contents_region` FOREIGN KEY (`region_id`) REFERENCES `regions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관광 콘텐츠 공통 원본';

CREATE TABLE `web_push_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notification_id` bigint NOT NULL,
  `recipient_member_id` bigint NOT NULL,
  `subscription_id` bigint NOT NULL,
  `payload_json` json NOT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'PENDING',
  `attempt_count` int NOT NULL DEFAULT '0',
  `next_attempt_at` datetime(6) NOT NULL,
  `last_error_code` varchar(100) DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `claim_token` varchar(36) DEFAULT NULL,
  `processing_started_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_web_push_outbox_notification_subscription` (`notification_id`,`subscription_id`),
  KEY `ix_web_push_outbox_due` (`status`,`next_attempt_at`,`id`),
  KEY `ix_web_push_outbox_subscription_status` (`subscription_id`,`status`),
  KEY `ix_web_push_outbox_processing_lease` (`status`,`processing_started_at`,`id`),
  CONSTRAINT `ck_web_push_outbox_attempt_count` CHECK ((`attempt_count` >= 0)),
  CONSTRAINT `ck_web_push_outbox_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'RETRY_WAIT',_utf8mb4'SENT',_utf8mb4'FAILED_PERMANENT',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Web Push 발송 작업과 재처리 상태를 보관하는 Outbox';

CREATE TABLE `web_push_subscriptions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `endpoint` text CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `endpoint_hash` binary(32) NOT NULL,
  `p256dh` varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `auth_secret` varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `expiration_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `failure_count` int NOT NULL DEFAULT '0',
  `last_success_at` datetime(6) DEFAULT NULL,
  `last_failure_at` datetime(6) DEFAULT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `session_hash` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_web_push_subscriptions_endpoint_hash` (`endpoint_hash`),
  KEY `ix_web_push_subscriptions_member_status` (`member_id`,`status`),
  KEY `ix_web_push_subscriptions_session_status` (`session_hash`,`status`),
  CONSTRAINT `fk_web_push_subscriptions_member` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
  CONSTRAINT `ck_web_push_subscriptions_failure_count` CHECK ((`failure_count` >= 0)),
  CONSTRAINT `ck_web_push_subscriptions_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'INVALID',_utf8mb4'REVOKED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원 브라우저별 표준 Web Push 구독';

SET FOREIGN_KEY_CHECKS = @previous_foreign_key_checks;
