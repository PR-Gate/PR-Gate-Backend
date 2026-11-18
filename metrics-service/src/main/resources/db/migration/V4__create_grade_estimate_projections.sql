CREATE TABLE grade_estimate_projections (
    evidence_hash VARCHAR(64) PRIMARY KEY,
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
