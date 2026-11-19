CREATE TABLE grade_estimate_jobs (
    evaluation_id UUID PRIMARY KEY REFERENCES rubric_evaluations(evaluation_id) ON DELETE CASCADE,
    revision BIGINT NOT NULL DEFAULT 1,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_id UUID,
    lease_until TIMESTAMPTZ
);

CREATE INDEX ix_grade_estimate_jobs_due ON grade_estimate_jobs(next_attempt_at);

CREATE FUNCTION enqueue_grade_estimate() RETURNS trigger
LANGUAGE plpgsql SET search_path = metrics, pg_temp AS $$
BEGIN
    IF OLD.summary IS DISTINCT FROM NEW.summary
        OR OLD.decisions IS DISTINCT FROM NEW.decisions
        OR OLD.evidence_state IS DISTINCT FROM NEW.evidence_state THEN
        INSERT INTO grade_estimate_jobs(evaluation_id) VALUES (NEW.evaluation_id)
        ON CONFLICT (evaluation_id) DO UPDATE SET
            revision = grade_estimate_jobs.revision + 1,
            attempts = 0,
            next_attempt_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER rubric_evaluation_estimate
AFTER UPDATE OF summary, decisions, evidence_state ON rubric_evaluations
FOR EACH ROW EXECUTE FUNCTION enqueue_grade_estimate();

INSERT INTO grade_estimate_jobs(evaluation_id)
SELECT evaluation_id FROM rubric_evaluations WHERE summary <> '{}'::jsonb;
