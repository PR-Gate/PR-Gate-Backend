ALTER TABLE analysis_event_outbox ADD COLUMN routing_key TEXT NOT NULL DEFAULT 'cicd.analysis.completed.v1';
CREATE TABLE quality_gate_requests (
    event_id UUID PRIMARY KEY,
    repository_id BIGINT NOT NULL,
    policy_version BIGINT NOT NULL,
    payload TEXT NOT NULL,
    state TEXT NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','APPLIED','FAILED','SUPERSEDED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    UNIQUE(repository_id, policy_version)
);
