package com.prgate.metrics.infrastructure.inbound.messaging;

import com.prgate.metrics.application.service.GradeEstimationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class GradeEstimateWorker {
    private final JdbcTemplate jdbc;
    private final GradeEstimationService grades;

    public GradeEstimateWorker(JdbcTemplate jdbc, GradeEstimationService grades) {
        this.jdbc = jdbc;
        this.grades = grades;
    }

    @Scheduled(fixedDelayString = "${app.evaluation.estimate-delay-ms:10000}",
            initialDelayString = "${app.evaluation.estimate-delay-ms:10000}")
    public void processNext() {
        UUID lease = UUID.randomUUID();
        var jobs = jdbc.query("""
                WITH due AS (
                    SELECT evaluation_id FROM metrics.grade_estimate_jobs
                    WHERE next_attempt_at <= CURRENT_TIMESTAMP
                        AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP)
                    ORDER BY next_attempt_at, evaluation_id LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE metrics.grade_estimate_jobs job
                SET lease_id=?, lease_until=CURRENT_TIMESTAMP + INTERVAL '3 minutes'
                FROM due WHERE due.evaluation_id=job.evaluation_id
                RETURNING job.evaluation_id, job.revision
                """, (row, index) -> new Job(row.getObject(1, UUID.class), row.getLong(2)), lease);
        for (var job : jobs) {
            try {
                grades.generateForWorker(job.id());
                jdbc.update("""
                        DELETE FROM metrics.grade_estimate_jobs WHERE evaluation_id=? AND revision=? AND lease_id=?
                        """, job.id(), job.revision(), lease);
            } catch (RuntimeException failure) {
                jdbc.update("""
                        UPDATE metrics.grade_estimate_jobs
                        SET attempts=attempts+1,
                            next_attempt_at=CURRENT_TIMESTAMP + LEAST(attempts+1, 20) * INTERVAL '30 seconds'
                        WHERE evaluation_id=? AND revision=? AND lease_id=?
                        """, job.id(), job.revision(), lease);
            } finally {
                jdbc.update("""
                        UPDATE metrics.grade_estimate_jobs SET lease_id=NULL, lease_until=NULL
                        WHERE evaluation_id=? AND lease_id=?
                        """, job.id(), lease);
            }
        }
    }

    private record Job(UUID id, long revision) { }
}
