CREATE SEQUENCE quality_gate_policy_versions;
CREATE TABLE quality_gate_outbox (
    event_id UUID PRIMARY KEY,
    configuration_id UUID NOT NULL REFERENCES evaluation_configurations(configuration_id),
    policy_version BIGINT NOT NULL UNIQUE,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMPTZ,
    result_state TEXT,
    gate_name TEXT,
    sonar_project_key TEXT,
    last_error TEXT
);
CREATE INDEX ix_gate_outbox_configuration ON quality_gate_outbox(configuration_id, policy_version DESC);
