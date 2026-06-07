ALTER TABLE analysis_jobs
    ADD COLUMN IF NOT EXISTS detect_distributed_monoliths BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS detect_api_versioning_absence BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS detect_wrong_cuts BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS detect_esb_misuse BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS god_service_field_count INTEGER DEFAULT 25,
    ADD COLUMN IF NOT EXISTS god_service_public_methods INTEGER DEFAULT 30,
    ADD COLUMN IF NOT EXISTS god_service_loc INTEGER DEFAULT 1000,
    ADD COLUMN IF NOT EXISTS god_service_import_domains INTEGER DEFAULT 20,
    ADD COLUMN IF NOT EXISTS god_service_constructor_params INTEGER DEFAULT 12,
    ADD COLUMN IF NOT EXISTS god_service_tcc_threshold DOUBLE PRECISION DEFAULT 0.5,
    ADD COLUMN IF NOT EXISTS god_service_min_metrics INTEGER DEFAULT 3,
    ADD COLUMN IF NOT EXISTS esb_mediator_threshold DOUBLE PRECISION DEFAULT 0.4,
    ADD COLUMN IF NOT EXISTS distributed_monolith_high_coupling DOUBLE PRECISION DEFAULT 0.5,
    ADD COLUMN IF NOT EXISTS distributed_monolith_connected_ratio DOUBLE PRECISION DEFAULT 0.8,
    ADD COLUMN IF NOT EXISTS distributed_monolith_moderate_coupling DOUBLE PRECISION DEFAULT 0.3;

UPDATE analysis_jobs
SET god_service_min_metrics = COALESCE(god_service_min_metrics, god_service_min_domains, 3)
WHERE god_service_min_metrics IS NULL;
