-- 기존 HTTP 작업은 PENDING을 유지하며 MQ 작업도 회원당 활성 작업 제한에 포함한다.
ALTER TABLE generation_jobs
    MODIFY COLUMN active_member_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('PENDING', 'PUBLISH_PENDING', 'QUEUED', 'PROCESSING')
            THEN member_id ELSE NULL END
    ) STORED;
