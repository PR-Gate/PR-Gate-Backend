ALTER TABLE pipeline_runs ADD COLUMN evaluation_repository_id UUID
    REFERENCES evaluation_repositories(id);
ALTER TABLE pipeline_runs ADD COLUMN academic_project_id UUID;

UPDATE pipeline_runs AS run
SET evaluation_repository_id = repository.id, academic_project_id = repository.academic_project_id
FROM evaluation_repositories AS repository
WHERE run.github_repository_id = repository.github_repository_id;

-- Unmatched legacy executions remain readable without inventing an academic association.
ALTER TABLE pipeline_runs ADD CONSTRAINT ck_pipeline_run_project_reference
    CHECK ((evaluation_repository_id IS NULL AND academic_project_id IS NULL)
        OR (evaluation_repository_id IS NOT NULL AND academic_project_id IS NOT NULL));
CREATE INDEX ix_pipeline_runs_academic_project ON pipeline_runs (academic_project_id, created_at DESC);
