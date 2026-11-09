CREATE TABLE evaluation_repositories (
    id UUID PRIMARY KEY,
    academic_project_id UUID NOT NULL,
    github_repository_id BIGINT NOT NULL UNIQUE CHECK (github_repository_id > 0),
    repository_owner TEXT NOT NULL,
    repository_name TEXT NOT NULL,
    sonar_project_key VARCHAR(255) NOT NULL UNIQUE,
    analysis_branch VARCHAR(255) NOT NULL CHECK (length(trim(analysis_branch)) > 0),
    provisioning_status VARCHAR(20) NOT NULL
        CHECK (provisioning_status IN ('PENDING', 'READY', 'FAILED')),
    registered_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL CHECK (updated_at >= registered_at)
);

CREATE INDEX ix_evaluation_repositories_project ON evaluation_repositories (academic_project_id);
