package com.prgate.metrics.infrastructure.outbound.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.metrics.application.dto.AnalysisCompletedEvent;
import com.prgate.metrics.application.port.out.AnalysisSnapshotStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAnalysisSnapshotStore implements AnalysisSnapshotStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcAnalysisSnapshotStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean saveIfAbsent(AnalysisCompletedEvent event) {
        String payload = serialize(event);
        int inserted = jdbc.update("""
                INSERT INTO metrics.analysis_snapshots
                    (event_id, repository_id, git_ref, commit_sha, workflow_run_id,
                     workflow_run_attempt, sonar_project_key, sonar_analysis_id,
                     sonar_task_id, analyzed_at, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (event_id) DO NOTHING
                """, event.eventId(), event.repositoryId(), event.gitRef(), event.commitSha(),
                event.workflowRunId(), event.workflowRunAttempt(), event.sonarProjectKey(),
                event.sonarAnalysisId(), event.sonarTaskId(),
                java.sql.Timestamp.from(event.analyzedAt()), payload);
        if (inserted == 1) {
            jdbc.update("INSERT INTO metrics.analysis_evidence(event_id,state) VALUES (?, 'PENDING')", event.eventId());
            return true;
        }
        Boolean identical = jdbc.queryForObject("""
                SELECT payload = CAST(? AS jsonb)
                FROM metrics.analysis_snapshots WHERE event_id = ?
                """, Boolean.class, payload, event.eventId());
        if (!Boolean.TRUE.equals(identical)) {
            throw new IllegalArgumentException("Event ID already exists with different contents");
        }
        return false;
    }

    private String serialize(AnalysisCompletedEvent event) {
        try { return json.writeValueAsString(event); }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Analysis event cannot be serialized", exception);
        }
    }
}
