CREATE TABLE users.github_account_links (
    user_id UUID PRIMARY KEY REFERENCES users.users(id),
    github_user_id BIGINT NOT NULL UNIQUE CHECK (github_user_id > 0),
    github_login VARCHAR(100) NOT NULL,
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE users.github_authorizations (
    state_hash VARCHAR(43) PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users.users(id),
    verifier VARCHAR(43) NOT NULL,
    browser_hash VARCHAR(43) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_github_authorizations_expiry ON users.github_authorizations(expires_at);
