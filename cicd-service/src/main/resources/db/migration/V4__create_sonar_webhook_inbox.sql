ALTER TABLE pipeline_runs DROP CONSTRAINT ck_pipeline_run_sonar_analysis_complete;
ALTER TABLE pipeline_runs ADD CONSTRAINT ck_pipeline_run_sonar_analysis_complete CHECK (
    (sonar_analysis_id IS NULL AND sonar_quality_gate_status IS NULL AND sonar_dashboard_url IS NULL
        AND sonar_analyzed_at IS NULL AND sonar_coverage_percent IS NULL AND sonar_vulnerabilities IS NULL
        AND sonar_duplicated_lines_percent IS NULL AND sonar_technical_debt_percent IS NULL)
    OR (sonar_analysis_id IS NOT NULL AND sonar_quality_gate_status IS NOT NULL
        AND sonar_dashboard_url IS NOT NULL AND sonar_analyzed_at IS NOT NULL)
);

CREATE TABLE sonar_webhook_inbox (
    payload_hash VARCHAR(64) PRIMARY KEY,
    payload TEXT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0,
    processed_at TIMESTAMPTZ,
    last_error VARCHAR(100)
);
CREATE UNIQUE INDEX uq_pipeline_runs_sonar_analysis ON pipeline_runs(sonar_analysis_id)
    WHERE sonar_analysis_id IS NOT NULL;
CREATE INDEX ix_sonar_inbox_pending ON sonar_webhook_inbox(next_attempt_at)
    WHERE processed_at IS NULL;
