-- V4 — Scope detected microservices to the analysis job that produced them.
-- This preserves historical analysis snapshots across re-analysis.

ALTER TABLE microservices
    ADD COLUMN IF NOT EXISTS analysis_job_id BIGINT;

-- Existing installations only have the latest mutable project-level service set.
-- Attach those rows to the latest job for the same project as a best-effort backfill.
UPDATE microservices m
SET analysis_job_id = latest_job.id
FROM (
    SELECT DISTINCT ON (project_id) id, project_id
    FROM analysis_jobs
    ORDER BY project_id, created_at DESC, id DESC
) AS latest_job
WHERE m.project_id = latest_job.project_id
  AND m.analysis_job_id IS NULL;

ALTER TABLE microservices
    ADD CONSTRAINT fk_microservice_analysis_job
    FOREIGN KEY (analysis_job_id)
    REFERENCES analysis_jobs(id)
    ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_microservice_analysis_job
    ON microservices (analysis_job_id);
