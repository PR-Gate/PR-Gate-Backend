CREATE TABLE analysis_snapshots (
    event_id UUID PRIMARY KEY,
    repository_id BIGINT NOT NULL CHECK (repository_id > 0),
    git_ref TEXT NOT NULL,
    commit_sha VARCHAR(64) NOT NULL,
    workflow_run_id BIGINT NOT NULL CHECK (workflow_run_id > 0),
    workflow_run_attempt INTEGER NOT NULL CHECK (workflow_run_attempt > 0),
    sonar_project_key TEXT NOT NULL,
    sonar_analysis_id TEXT NOT NULL,
    sonar_task_id TEXT NOT NULL,
    analyzed_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_analysis_repository_revision
    ON analysis_snapshots(repository_id, git_ref, commit_sha);
CREATE INDEX ix_analysis_sonar_reference
    ON analysis_snapshots(sonar_project_key, sonar_analysis_id);
