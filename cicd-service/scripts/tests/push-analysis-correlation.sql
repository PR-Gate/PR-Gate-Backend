-- Run only in a disposable database after migrations V6, V7 and V8.
BEGIN;
INSERT INTO sonar_webhook_receipts (payload_hash, project_key, task_id, status, payload)
VALUES ('test-receipt', 'test-project', 'test-task', 'SUCCESS', jsonb_build_object(
    'revision', repeat('b', 40),
    'qualityGate', jsonb_build_object('status', 'ERROR'),
    'properties', jsonb_build_object(
        'sonar.analysis.prgateRepositoryId', '123',
        'sonar.analysis.prgatePushRef', 'refs/heads/main',
        'sonar.analysis.prgatePushBefore', repeat('a', 40),
        'sonar.analysis.prgateWorkflowRunId', '456',
        'sonar.analysis.prgateWorkflowRunAttempt', '1'
    ))::text);
INSERT INTO github_push_receipts
    (delivery_id, repository_id, owner_login, repository_name, git_ref,
     before_sha, after_sha, deleted, commits_in_payload)
VALUES ('00000000-0000-0000-0000-000000000001', 123, 'test', 'backend',
        'refs/heads/main', repeat('a', 40), repeat('b', 40), false, 1);
DO $$ BEGIN
    IF (SELECT count(*) FROM push_analysis_receipts WHERE task_id = 'test-task'
        AND workflow_run_id = '456' AND analysis_status = 'SUCCESS'
        AND quality_gate_status = 'ERROR') <> 1 THEN
        RAISE EXCEPTION 'Result arriving before push must correlate, preserving failed Quality Gate';
    END IF;
END $$;

-- Neither a different repository, branch, baseline, revision nor deletion matches.
INSERT INTO github_push_receipts
SELECT ('00000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
       CASE WHEN n = 2 THEN 124 ELSE 123 END, 'test', 'other',
       CASE WHEN n = 3 THEN 'refs/heads/other' ELSE 'refs/heads/main' END,
       CASE WHEN n = 4 THEN repeat('c', 40) ELSE repeat('a', 40) END,
       CASE WHEN n = 5 THEN repeat('c', 40) ELSE repeat('b', 40) END,
       n = 6, 1, CURRENT_TIMESTAMP
FROM generate_series(2, 6) n;
DO $$ BEGIN
    IF (SELECT count(*) FROM push_analysis_receipts WHERE task_id IS NULL) <> 5 THEN
        RAISE EXCEPTION 'Mismatched pushes must remain uncorrelated';
    END IF;
END $$;

-- A new attempt remains a separate result, even for the same push.
INSERT INTO sonar_webhook_receipts (payload_hash, project_key, task_id, status, payload)
SELECT 'test-retry', project_key, 'retry-task', status,
       jsonb_set(payload::jsonb,
           '{properties,sonar.analysis.prgateWorkflowRunAttempt}', '"2"')::text
FROM sonar_webhook_receipts WHERE payload_hash = 'test-receipt';
DO $$ BEGIN
    IF (SELECT count(*) FROM push_analysis_receipts WHERE task_id IS NOT NULL) <> 2 THEN
        RAISE EXCEPTION 'Run attempts must remain separate';
    END IF;
END $$;
ROLLBACK;
