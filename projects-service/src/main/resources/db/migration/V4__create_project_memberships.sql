ALTER TABLE projects ADD CONSTRAINT uq_projects_id_course UNIQUE (id, course_id);

CREATE TABLE project_memberships (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    project_id UUID NOT NULL,
    student_id UUID NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    left_at TIMESTAMPTZ,
    CONSTRAINT fk_memberships_project_course FOREIGN KEY (project_id, course_id)
        REFERENCES projects (id, course_id),
    CONSTRAINT ck_memberships_dates CHECK (left_at IS NULL OR left_at >= joined_at)
);

CREATE UNIQUE INDEX uq_memberships_active_student_course
    ON project_memberships (course_id, student_id) WHERE left_at IS NULL;
CREATE INDEX idx_memberships_project ON project_memberships (project_id);
