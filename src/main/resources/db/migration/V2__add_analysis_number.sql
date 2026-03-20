-- V2 — Add analysis_number to analysis_jobs for re-analysis / "New Code" tracking

ALTER TABLE analysis_jobs
    ADD COLUMN IF NOT EXISTS analysis_number INTEGER DEFAULT 1;

-- Back-fill: number each project's jobs chronologically
UPDATE analysis_jobs
SET analysis_number = sub.rn
FROM (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY created_at) AS rn
    FROM analysis_jobs
) AS sub
WHERE analysis_jobs.id = sub.id;

