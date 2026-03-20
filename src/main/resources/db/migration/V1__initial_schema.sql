-- V1 — Initial schema

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    email           VARCHAR(255)    NOT NULL UNIQUE,
    password        VARCHAR(255)    NOT NULL,
    github_token    VARCHAR(255),
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP
);

CREATE UNIQUE INDEX idx_user_email ON users (email);

CREATE TABLE projects (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    description     VARCHAR(1000),
    source_type     VARCHAR(50)     NOT NULL,      -- GITHUB | GITLAB | UPLOAD
    source_url      VARCHAR(500),
    branch          VARCHAR(100)    DEFAULT 'main',
    is_private      BOOLEAN         DEFAULT FALSE,
    local_path      VARCHAR(255),
    owner_id        BIGINT          REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP
);

CREATE INDEX idx_project_owner ON projects (owner_id);
CREATE INDEX idx_project_name  ON projects (name);

CREATE TABLE microservices (
    id                  BIGSERIAL       PRIMARY KEY,
    name                VARCHAR(255)    NOT NULL,
    relative_path       VARCHAR(500),
    lines_of_code       INTEGER         DEFAULT 0,
    number_of_classes   INTEGER         DEFAULT 0,
    number_of_endpoints INTEGER         DEFAULT 0,
    number_of_entities  INTEGER         DEFAULT 0,
    datasource_url      VARCHAR(500),
    datasource_name     VARCHAR(255),
    project_id          BIGINT          NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP
);

CREATE INDEX idx_microservice_project ON microservices (project_id);
CREATE INDEX idx_microservice_name    ON microservices (name);

CREATE TABLE endpoints (
    id                  BIGSERIAL       PRIMARY KEY,
    path                VARCHAR(500)    NOT NULL,
    http_method         VARCHAR(50)     NOT NULL,  -- GET | POST | PUT | DELETE | PATCH | HEAD | OPTIONS
    controller_class    VARCHAR(255),
    method_name         VARCHAR(255),
    api_version         VARCHAR(100),
    has_versioning      BOOLEAN         DEFAULT FALSE,
    microservice_id     BIGINT          NOT NULL REFERENCES microservices (id) ON DELETE CASCADE,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP
);

CREATE INDEX idx_endpoint_microservice ON endpoints (microservice_id);
CREATE INDEX idx_endpoint_path         ON endpoints (path);

CREATE TABLE service_dependencies (
    id                  BIGSERIAL       PRIMARY KEY,
    source_service_id   BIGINT          NOT NULL REFERENCES microservices (id) ON DELETE CASCADE,
    target_service_id   BIGINT          NOT NULL REFERENCES microservices (id) ON DELETE CASCADE,
    dependency_type     VARCHAR(50)     NOT NULL,  -- REST_SYNC | MESSAGE_ASYNC | DATABASE | GRPC | FEIGN_CLIENT | REST_TEMPLATE | WEB_CLIENT
    call_count          INTEGER         DEFAULT 1,
    evidence_file       VARCHAR(500),
    evidence_line       INTEGER,
    evidence_code       VARCHAR(1000),
    target_url          VARCHAR(500),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP
);

CREATE INDEX idx_dependency_source ON service_dependencies (source_service_id);
CREATE INDEX idx_dependency_target ON service_dependencies (target_service_id);

CREATE TABLE code_smells (
    id                  BIGSERIAL       PRIMARY KEY,
    smell_type          VARCHAR(100)    NOT NULL,
    description         VARCHAR(500),
    file_path           VARCHAR(500),
    class_name          VARCHAR(255),
    method_name         VARCHAR(255),
    line_number         INTEGER,
    severity            VARCHAR(50)     NOT NULL DEFAULT 'MEDIUM',  -- LOW | MEDIUM | HIGH | CRITICAL
    source_tool         VARCHAR(100)    DEFAULT 'DesigniteJava',
    microservice_id     BIGINT          NOT NULL REFERENCES microservices (id) ON DELETE CASCADE,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP
);

CREATE INDEX idx_codesmell_microservice ON code_smells (microservice_id);
CREATE INDEX idx_codesmell_type         ON code_smells (smell_type);

CREATE TABLE analysis_jobs (
    id                              BIGSERIAL       PRIMARY KEY,
    project_id                      BIGINT          NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    status                          VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    current_phase                   VARCHAR(255),
    current_service                 VARCHAR(255),
    services_completed              INTEGER         DEFAULT 0,
    total_services                  INTEGER         DEFAULT 0,
    progress_percentage             INTEGER         DEFAULT 0,
    started_at                      TIMESTAMP,
    completed_at                    TIMESTAMP,
    error_message                   VARCHAR(2000),

    run_designite                   BOOLEAN         DEFAULT TRUE,
    detect_cyclic_dependencies      BOOLEAN         DEFAULT TRUE,
    detect_shared_databases         BOOLEAN         DEFAULT TRUE,
    detect_nano_services            BOOLEAN         DEFAULT TRUE,
    detect_god_services             BOOLEAN         DEFAULT TRUE,
    detect_chatty_services          BOOLEAN         DEFAULT TRUE,
    detect_hardcoded_endpoints      BOOLEAN         DEFAULT TRUE,

    nano_service_max_loc            INTEGER         DEFAULT 500,
    nano_service_max_endpoints      INTEGER         DEFAULT 2,
    god_service_min_domains         INTEGER         DEFAULT 3,
    chatty_service_min_calls        INTEGER         DEFAULT 5,

    created_at                      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP
);

CREATE INDEX idx_job_project ON analysis_jobs (project_id);
CREATE INDEX idx_job_status  ON analysis_jobs (status);

CREATE TABLE analysis_results (
    id                      BIGSERIAL       PRIMARY KEY,
    analysis_job_id         BIGINT          NOT NULL UNIQUE REFERENCES analysis_jobs (id) ON DELETE CASCADE,
    health_score            INTEGER         DEFAULT 100,
    services_analyzed       INTEGER         DEFAULT 0,
    total_anti_patterns     INTEGER         DEFAULT 0,
    total_code_smells       INTEGER         DEFAULT 0,
    critical_issues         INTEGER         DEFAULT 0,
    high_issues             INTEGER         DEFAULT 0,
    medium_issues           INTEGER         DEFAULT 0,
    low_issues              INTEGER         DEFAULT 0,
    total_lines_of_code     INTEGER         DEFAULT 0,
    average_service_size    DOUBLE PRECISION DEFAULT 0.0,
    total_dependencies      INTEGER         DEFAULT 0,
    cycle_count             INTEGER         DEFAULT 0,
    coupling_coefficient    DOUBLE PRECISION DEFAULT 0.0,
    dependency_graph_json   TEXT,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP
);

CREATE INDEX idx_result_job ON analysis_results (analysis_job_id);


CREATE TABLE detected_anti_patterns (
    id                      BIGSERIAL       PRIMARY KEY,
    analysis_result_id      BIGINT          NOT NULL REFERENCES analysis_results (id) ON DELETE CASCADE,
    pattern_type            VARCHAR(50)     NOT NULL,  --
    severity                VARCHAR(50)     NOT NULL DEFAULT 'MEDIUM',
    description             VARCHAR(1000),
    affected_services       TEXT,           -- JSON array
    details_json            TEXT,           -- JSON object
    remediation             VARCHAR(2000),
    evidence_json           TEXT,           -- JSON object
    cycle_length            INTEGER,
    shared_database_url     VARCHAR(500),
    primary_service_id      BIGINT          REFERENCES microservices (id) ON DELETE SET NULL,
    call_count              INTEGER,
    code_snippets           TEXT,           -- JSON array
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP
);

CREATE INDEX idx_antipattern_result   ON detected_anti_patterns (analysis_result_id);
CREATE INDEX idx_antipattern_type     ON detected_anti_patterns (pattern_type);
CREATE INDEX idx_antipattern_severity ON detected_anti_patterns (severity);

