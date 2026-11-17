-- A query-time association also handles Sonar arriving before the GitHub delivery.
-- Keep every run attempt; do not collapse different analyses of the same revision.
CREATE VIEW push_analysis_receipts AS
SELECT p.delivery_id,
       p.repository_id,
       p.owner_login,
       p.repository_name,
       p.git_ref,
       p.before_sha,
       p.after_sha,
       p.received_at AS push_received_at,
       s.payload_hash AS sonar_receipt_hash,
       s.project_key,
       s.task_id,
       s.status AS analysis_status,
       s.received_at AS sonar_received_at,
       s.payload::jsonb -> 'qualityGate' ->> 'status' AS quality_gate_status,
       s.payload::jsonb -> 'properties' ->> 'sonar.analysis.prgateWorkflowRunId' AS workflow_run_id,
       s.payload::jsonb -> 'properties' ->> 'sonar.analysis.prgateWorkflowRunAttempt' AS workflow_run_attempt
FROM github_push_receipts p
LEFT JOIN sonar_webhook_receipts s
  ON s.payload::jsonb -> 'properties' ->> 'sonar.analysis.prgateRepositoryId' = p.repository_id::text
 AND s.payload::jsonb -> 'properties' ->> 'sonar.analysis.prgatePushRef' = p.git_ref
 AND s.payload::jsonb -> 'properties' ->> 'sonar.analysis.prgatePushBefore' = p.before_sha
 AND s.payload::jsonb ->> 'revision' = p.after_sha
 AND NULLIF(s.payload::jsonb -> 'properties' ->> 'sonar.analysis.prgateWorkflowRunId', '') IS NOT NULL
 AND NULLIF(s.payload::jsonb -> 'properties' ->> 'sonar.analysis.prgateWorkflowRunAttempt', '') IS NOT NULL
 AND NOT p.deleted;

COMMENT ON VIEW push_analysis_receipts IS
    'Technical receipt association, not project authorization or independently verified GitHub run ownership. A missing result does not imply a failed or skipped analysis.';
