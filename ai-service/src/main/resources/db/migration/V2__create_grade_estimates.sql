CREATE TABLE grade_estimates (
    request_hash VARCHAR(64) PRIMARY KEY,
    estimate_id UUID NOT NULL UNIQUE,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
