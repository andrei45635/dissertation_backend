-- Restores flyway_schema_history rows after they were deleted, WITHOUT touching data.
-- Checksums below were computed from the actual V1..V7 files in src/main/resources/db/migration.
-- Run against the msadetector database. Safe to re-run (TRUNCATE first clears any partial rows).

TRUNCATE TABLE flyway_schema_history;

INSERT INTO flyway_schema_history
  (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES
  (1, '1', 'initial schema',                        'SQL', 'V1__initial_schema.sql',                    429756944,  'postgres', now(), 0, true),
  (2, '2', 'add analysis number',                   'SQL', 'V2__add_analysis_number.sql',              -1004297616, 'postgres', now(), 0, true),
  (3, '3', 'add detection confidence',              'SQL', 'V3__add_detection_confidence.sql',          1289614074, 'postgres', now(), 0, true),
  (4, '4', 'scope microservices to analysis jobs',  'SQL', 'V4__scope_microservices_to_analysis_jobs.sql', 687992185, 'postgres', now(), 0, true),
  (5, '5', 'align chatty threshold default',        'SQL', 'V5__align_chatty_threshold_default.sql',    -866688788, 'postgres', now(), 0, true),
  (6, '6', 'add job detector configuration',        'SQL', 'V6__add_job_detector_configuration.sql',     1826662348, 'postgres', now(), 0, true),
  (7, '7', 'enforce chatty threshold default',      'SQL', 'V7__enforce_chatty_threshold_default.sql',  -866688788, 'postgres', now(), 0, true);
