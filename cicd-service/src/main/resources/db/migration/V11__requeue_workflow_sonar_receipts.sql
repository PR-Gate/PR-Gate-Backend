INSERT INTO sonar_webhook_inbox (payload_hash, payload, received_at)
SELECT receipt.payload_hash, receipt.payload, receipt.received_at
FROM sonar_webhook_receipts receipt
JOIN evaluation_repositories repository ON repository.sonar_project_key = receipt.project_key
JOIN pipeline_runs run ON run.evaluation_repository_id = repository.id
    AND run.academic_project_id = repository.academic_project_id
    AND run.github_repository_id = repository.github_repository_id
    AND run.branch_name = repository.analysis_branch
    AND run.commit_sha = receipt.payload::jsonb ->> 'revision'
    AND run.github_run_id::text = receipt.payload::jsonb #>> '{properties,sonar.analysis.prgateWorkflowRunId}'
    AND run.github_run_attempt::text = receipt.payload::jsonb #>> '{properties,sonar.analysis.prgateWorkflowRunAttempt}'
WHERE receipt.status = 'SUCCESS'
    AND run.sonar_analysis_id IS NULL
    AND repository.github_repository_id::text = receipt.payload::jsonb #>> '{properties,sonar.analysis.prgateRepositoryId}'
    AND 'refs/heads/' || run.branch_name = receipt.payload::jsonb #>> '{properties,sonar.analysis.prgatePushRef}'
ON CONFLICT (payload_hash) DO NOTHING;
