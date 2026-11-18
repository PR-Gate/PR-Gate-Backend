CREATE TABLE analysis_export_receipts (
    source_hash VARCHAR(64) PRIMARY KEY REFERENCES sonar_webhook_receipts(payload_hash),
    exported_event_id UUID,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT
);

CREATE TABLE analysis_event_outbox (
    event_id UUID PRIMARY KEY,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMPTZ,
    last_error TEXT
);
CREATE INDEX ix_analysis_outbox_pending ON analysis_event_outbox(next_attempt_at)
    WHERE published_at IS NULL;
