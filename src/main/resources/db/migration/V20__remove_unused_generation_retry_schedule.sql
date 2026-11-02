-- 사용자 재시도는 즉시 요청으로 처리한다. MQ 발행 재시도 예약은 next_publish_at을 사용한다.
ALTER TABLE generation_jobs
    DROP INDEX ix_generation_jobs_next_attempt,
    DROP COLUMN next_attempt_at;
