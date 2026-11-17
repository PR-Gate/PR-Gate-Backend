CREATE TABLE sonar_webhook_receipts (
    payload_hash VARCHAR(64) PRIMARY KEY,
    project_key TEXT NOT NULL,
    task_id TEXT,
    status TEXT NOT NULL,
    payload TEXT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_sonar_receipts_task ON sonar_webhook_receipts(task_id);
