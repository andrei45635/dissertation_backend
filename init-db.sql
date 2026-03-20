-- Database initialization script
-- This runs automatically when the PostgreSQL container starts for the first time
-- Schema creation is handled by Flyway migrations (src/main/resources/db/migration/)

-- Create extensions if needed (also in V1 migration, but safe to repeat)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
