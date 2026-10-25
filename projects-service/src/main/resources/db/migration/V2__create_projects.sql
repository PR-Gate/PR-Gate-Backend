CREATE TABLE projects (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    leader_id UUID,
    github_org_id BIGINT,
    github_org_login TEXT,
    CONSTRAINT fk_projects_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT ck_projects_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_projects_github_org CHECK (
        (github_org_id IS NULL AND github_org_login IS NULL)
        OR (
            github_org_id IS NOT NULL
            AND github_org_login IS NOT NULL
            AND github_org_id > 0
            AND btrim(github_org_login) <> ''
        )
    )
);

CREATE INDEX idx_projects_course ON projects (course_id);
