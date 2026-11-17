CREATE TABLE github_push_receipts (
    delivery_id UUID PRIMARY KEY,
    repository_id BIGINT NOT NULL CHECK (repository_id > 0),
    owner_login TEXT NOT NULL,
    repository_name TEXT NOT NULL,
    git_ref TEXT NOT NULL,
    before_sha VARCHAR(64) NOT NULL,
    after_sha VARCHAR(64) NOT NULL,
    deleted BOOLEAN NOT NULL,
    commits_in_payload INTEGER NOT NULL CHECK (commits_in_payload >= 0),
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_push_receipts_repository ON github_push_receipts(repository_id, received_at);
