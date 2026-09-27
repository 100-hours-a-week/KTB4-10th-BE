ALTER TABLE generation_jobs
    ADD CONSTRAINT uq_generation_jobs_ai_job_id UNIQUE (ai_job_id);
