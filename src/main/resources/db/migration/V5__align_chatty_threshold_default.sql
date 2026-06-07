ALTER TABLE analysis_jobs
    ALTER COLUMN chatty_service_min_calls SET DEFAULT 5;

UPDATE analysis_jobs
SET chatty_service_min_calls = 5
WHERE chatty_service_min_calls IS NULL
   OR chatty_service_min_calls = 10;
