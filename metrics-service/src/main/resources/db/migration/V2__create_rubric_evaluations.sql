CREATE TABLE analysis_evidence (
    event_id UUID PRIMARY KEY REFERENCES analysis_snapshots(event_id),
    state TEXT NOT NULL CHECK (state IN ('PENDING','COLLECTED','UNAVAILABLE')),
    measures JSONB NOT NULL DEFAULT '{}',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    collected_at TIMESTAMPTZ,
    last_error TEXT
);
INSERT INTO analysis_evidence(event_id, state) SELECT event_id, 'PENDING' FROM analysis_snapshots;

CREATE TABLE published_rubrics (
    rubric_id UUID NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    project_id UUID NOT NULL,
    payload JSONB NOT NULL,
    published_by UUID NOT NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (rubric_id, version)
);

CREATE TABLE evaluation_configurations (
    configuration_id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    repository_id BIGINT NOT NULL CHECK (repository_id > 0),
    organization TEXT NOT NULL,
    git_ref TEXT NOT NULL,
    payload JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    approved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ix_active_repository_configuration
    ON evaluation_configurations(repository_id, git_ref) WHERE active;

CREATE TABLE rubric_evaluations (
    evaluation_id UUID PRIMARY KEY,
    configuration_id UUID NOT NULL REFERENCES evaluation_configurations(configuration_id),
    event_id UUID NOT NULL REFERENCES analysis_snapshots(event_id),
    project_id UUID NOT NULL,
    summary JSONB NOT NULL,
    decisions JSONB NOT NULL DEFAULT '[]',
    status TEXT NOT NULL CHECK (status IN ('PARTIAL','COMPLETED')),
    evidence_state TEXT NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(configuration_id, event_id)
);
CREATE INDEX ix_project_evaluations ON rubric_evaluations(project_id, evaluated_at DESC);
