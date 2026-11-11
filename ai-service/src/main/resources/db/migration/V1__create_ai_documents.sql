CREATE TABLE ai_documents (
    kind VARCHAR(32) NOT NULL,
    id VARCHAR(36) NOT NULL,
    version BIGINT NOT NULL,
    payload TEXT NOT NULL,
    PRIMARY KEY (kind, id)
);