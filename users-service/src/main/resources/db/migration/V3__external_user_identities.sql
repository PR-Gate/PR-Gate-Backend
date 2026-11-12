-- Keep existing Cognito identities and academic IDs unchanged.
ALTER TABLE users.users ALTER COLUMN cognito_sub DROP NOT NULL;
CREATE TABLE users.external_identities (
    issuer VARCHAR(512) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL REFERENCES users.users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (issuer, subject)
);
CREATE INDEX ix_external_identities_user ON users.external_identities(user_id);
