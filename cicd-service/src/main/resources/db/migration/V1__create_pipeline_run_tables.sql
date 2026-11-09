CREATE TABLE pipeline_runs (
    id UUID PRIMARY KEY,
    github_repository_id BIGINT NOT NULL,
    repository_owner VARCHAR(100) NOT NULL,
    repository_name VARCHAR(100) NOT NULL,
    github_run_id BIGINT NOT NULL,
    github_run_attempt INTEGER NOT NULL,
    workflow_name VARCHAR(200) NOT NULL,
    branch_name VARCHAR(255) NOT NULL,
    commit_sha VARCHAR(64) NOT NULL,
    trigger_type VARCHAR(30) NOT NULL,
    run_url TEXT NOT NULL,
    run_status VARCHAR(30) NOT NULL,
    conclusion VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    sonar_analysis_id VARCHAR(255),
    sonar_quality_gate_status VARCHAR(20),
    sonar_coverage_percent NUMERIC(5, 2),
    sonar_vulnerabilities INTEGER,
    sonar_duplicated_lines_percent NUMERIC(5, 2),
    sonar_technical_debt_percent NUMERIC(5, 2),
    sonar_dashboard_url TEXT,
    sonar_analyzed_at TIMESTAMPTZ,

    CONSTRAINT uq_pipeline_run_github_identity
        UNIQUE (github_repository_id, github_run_id, github_run_attempt),
    CONSTRAINT ck_pipeline_run_positive_github_ids
        CHECK (github_repository_id > 0 AND github_run_id > 0 AND github_run_attempt > 0),
    CONSTRAINT ck_pipeline_run_status
        CHECK (run_status IN ('QUEUED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_pipeline_run_trigger
        CHECK (trigger_type IN ('PUSH', 'PULL_REQUEST')),
    CONSTRAINT ck_pipeline_run_conclusion
        CHECK (conclusion IS NULL OR conclusion IN
            ('SUCCESS', 'FAILURE', 'CANCELLED', 'SKIPPED', 'TIMED_OUT',
             'ACTION_REQUIRED', 'NEUTRAL', 'STALE')),
    CONSTRAINT ck_pipeline_run_sonar_gate
        CHECK (sonar_quality_gate_status IS NULL OR sonar_quality_gate_status IN ('OK', 'WARN', 'ERROR', 'NONE')),
    CONSTRAINT ck_pipeline_run_sonar_metrics
        CHECK (
            (sonar_coverage_percent IS NULL OR sonar_coverage_percent BETWEEN 0 AND 100)
            AND (sonar_duplicated_lines_percent IS NULL OR sonar_duplicated_lines_percent BETWEEN 0 AND 100)
            AND (sonar_technical_debt_percent IS NULL OR sonar_technical_debt_percent BETWEEN 0 AND 100)
            AND (sonar_vulnerabilities IS NULL OR sonar_vulnerabilities >= 0)
        ),
    CONSTRAINT ck_pipeline_run_sonar_analysis_complete
        CHECK (
            (sonar_analysis_id IS NULL AND sonar_quality_gate_status IS NULL
                AND sonar_coverage_percent IS NULL AND sonar_vulnerabilities IS NULL
                AND sonar_duplicated_lines_percent IS NULL AND sonar_technical_debt_percent IS NULL
                AND sonar_dashboard_url IS NULL AND sonar_analyzed_at IS NULL)
            OR
            (sonar_analysis_id IS NOT NULL AND sonar_quality_gate_status IS NOT NULL
                AND sonar_coverage_percent IS NOT NULL AND sonar_vulnerabilities IS NOT NULL
                AND sonar_duplicated_lines_percent IS NOT NULL AND sonar_technical_debt_percent IS NOT NULL
                AND sonar_dashboard_url IS NOT NULL AND sonar_analyzed_at IS NOT NULL)
        ),
    CONSTRAINT ck_pipeline_run_state_timestamps
        CHECK (
            (run_status = 'QUEUED' AND started_at IS NULL AND completed_at IS NULL AND conclusion IS NULL)
            OR (run_status = 'IN_PROGRESS' AND started_at IS NOT NULL AND completed_at IS NULL AND conclusion IS NULL)
            OR (run_status = 'COMPLETED' AND completed_at IS NOT NULL AND conclusion IS NOT NULL)
        )
);

CREATE INDEX ix_pipeline_runs_repository_branch_created
    ON pipeline_runs (github_repository_id, branch_name, created_at DESC);

CREATE INDEX ix_pipeline_runs_created_at
    ON pipeline_runs (created_at DESC);

CREATE TABLE processed_webhook_deliveries (
    delivery_id UUID PRIMARY KEY,
    received_at TIMESTAMPTZ NOT NULL
);
