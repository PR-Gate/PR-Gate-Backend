CREATE TABLE student_practice_tests (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    project_id UUID NOT NULL,
    evaluation_id UUID NOT NULL REFERENCES rubric_evaluations(evaluation_id),
    questions JSONB NOT NULL,
    answers JSONB,
    correct_count INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMPTZ,
    UNIQUE (actor_id, evaluation_id)
);
CREATE INDEX ix_student_practice_project ON student_practice_tests(actor_id, project_id, created_at DESC);
