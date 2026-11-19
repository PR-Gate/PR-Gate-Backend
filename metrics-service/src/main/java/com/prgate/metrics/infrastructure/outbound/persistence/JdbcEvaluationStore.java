package com.prgate.metrics.infrastructure.outbound.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.metrics.application.dto.*;
import com.prgate.metrics.application.port.out.EvaluationStore;
import com.prgate.metrics.domain.model.valueobjects.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;

@Repository
public class JdbcEvaluationStore implements EvaluationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private static final String EVALUATION_SELECT = """
            SELECT e.*, s.repository_id, s.git_ref, s.commit_sha FROM metrics.rubric_evaluations e
            JOIN metrics.analysis_snapshots s ON s.event_id = e.event_id
            """;
    public JdbcEvaluationStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public void saveRubric(UUID project, PublishedRubric rubric, UUID actor) {
        jdbc.update("""
                INSERT INTO metrics.published_rubrics(rubric_id, version, project_id, payload, published_by)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?)
                """, rubric.id().value(), rubric.version().value(), project, write(rubric), actor);
    }
    public Optional<PublishedRubric> rubric(UUID project, UUID rubricId, int version) {
        return jdbc.query("SELECT payload FROM metrics.published_rubrics WHERE project_id=? AND rubric_id=? AND version=? AND archived_at IS NULL",
                (row, i) -> read(row.getString(1), PublishedRubric.class), project, rubricId, version).stream().findFirst();
    }
    public int latestRubricVersion(UUID project, UUID rubricId) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM metrics.published_rubrics WHERE project_id=? AND rubric_id=? AND archived_at IS NULL",
                Integer.class, project, rubricId);
    }
    public List<PublishedRubric> rubrics(UUID project,int page,int size) {
        return jdbc.query("""
                SELECT payload FROM (
                  SELECT DISTINCT ON (rubric_id) payload,published_at,rubric_id
                  FROM metrics.published_rubrics WHERE project_id=? AND archived_at IS NULL ORDER BY rubric_id,version DESC
                ) latest ORDER BY published_at DESC,rubric_id LIMIT ? OFFSET ?
                """,(row,i)->read(row.getString(1),PublishedRubric.class),project,size,(long)page*size);
    }
    public void lockCourseRubrics(UUID courseId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> {}, "course-rubrics:" + courseId);
    }
    public boolean archiveCourseRubric(UUID courseId, UUID rubricId) {
        return jdbc.update("""
                UPDATE metrics.published_rubrics SET archived_at=CURRENT_TIMESTAMP
                WHERE project_id=? AND rubric_id=? AND archived_at IS NULL
                """, courseId, rubricId) > 0;
    }
    public List<StoredConfiguration> configurations(UUID project,long repositoryId) {
        return jdbc.query("""
                SELECT payload FROM metrics.evaluation_configurations WHERE project_id=? AND repository_id=? AND active
                ORDER BY approved_at DESC
                """,(row,i)->read(row.getString(1),StoredConfiguration.class),project,repositoryId);
    }
    public List<RepositoryChoice> repositories(String organization) {
        return jdbc.query("""
                SELECT DISTINCT ON (repository_id,git_ref) repository_id,payload->>'repository' AS full_name,
                    git_ref,analyzed_at FROM metrics.analysis_snapshots
                WHERE lower(split_part(payload->>'repository','/',1))=lower(?)
                ORDER BY repository_id,git_ref,analyzed_at DESC
                """,(row,i)->new RepositoryChoice(row.getLong(1),row.getString(2),row.getString(3),row.getTimestamp(4).toInstant()),organization);
    }
    public List<AnalysisCompletedEvent> analyses(String organization,long repository,String gitRef,int page,int size) {
        return jdbc.query("""
                SELECT payload FROM metrics.analysis_snapshots WHERE repository_id=? AND git_ref=?
                  AND lower(split_part(payload->>'repository','/',1))=lower(?)
                ORDER BY analyzed_at DESC,event_id LIMIT ? OFFSET ?
                """,(row,i)->read(row.getString(1),AnalysisCompletedEvent.class),repository,gitRef,organization,size,(long)page*size);
    }
    @Transactional
    public void activateConfiguration(StoredConfiguration configuration) {
        jdbc.update("""
                UPDATE metrics.evaluation_configurations SET active=false
                WHERE project_id=? AND repository_id=? AND git_ref=? AND active
                """, configuration.projectId(), configuration.repositoryId(), configuration.gitRef());
        jdbc.update("""
                INSERT INTO metrics.evaluation_configurations(configuration_id, project_id, repository_id,
                    organization, git_ref, payload, approved_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                """, configuration.id(), configuration.projectId(), configuration.repositoryId(), configuration.organization(),
                configuration.gitRef(), write(configuration), java.sql.Timestamp.from(configuration.approvedAt()));
        enqueueGate(configuration);
    }
    private void enqueueGate(StoredConfiguration configuration) {
        long version = jdbc.queryForObject("SELECT nextval('metrics.quality_gate_policy_versions')", Long.class);
        var request = QualityGateRequest.from(configuration, UUID.randomUUID(), version);
        jdbc.update("""
                INSERT INTO metrics.quality_gate_outbox(event_id, configuration_id, policy_version, payload)
                VALUES (?, ?, ?, ?)
                """, request.eventId(), configuration.id(), version, write(request));
    }
    @Transactional
    public void retryGate(UUID configurationId) {
        var rows=jdbc.query("SELECT payload FROM metrics.evaluation_configurations WHERE configuration_id=? AND active FOR UPDATE",
                (row,i)->read(row.getString(1),StoredConfiguration.class),configurationId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Only an active configuration may be retried");
        enqueueGate(rows.getFirst());
    }
    public Optional<StoredConfiguration> configuration(UUID id) {
        return jdbc.query("SELECT payload FROM metrics.evaluation_configurations WHERE configuration_id=?",
                (row, i) -> read(row.getString(1), StoredConfiguration.class), id).stream().findFirst();
    }
    public Optional<AnalysisCompletedEvent> analysis(UUID eventId) {
        return jdbc.query("SELECT payload FROM metrics.analysis_snapshots WHERE event_id=?",
                (row, i) -> read(row.getString(1), AnalysisCompletedEvent.class), eventId).stream().findFirst();
    }
    public Map<String, Object> gateStatus(UUID configurationId) {
        return jdbc.queryForMap("""
                SELECT q.event_id AS request_id, q.policy_version,
                    CASE WHEN NOT c.active THEN 'SUPERSEDED'
                         ELSE COALESCE(q.result_state, CASE WHEN q.published_at IS NULL THEN 'PENDING' ELSE 'DELIVERED' END)
                    END AS synchronization_state,
                    q.gate_name, q.sonar_project_key
                FROM metrics.quality_gate_outbox q
                JOIN metrics.evaluation_configurations c ON c.configuration_id=q.configuration_id
                WHERE q.configuration_id=? ORDER BY q.policy_version DESC LIMIT 1
                """, configurationId);
    }
    public Evidence evidence(UUID eventId) {
        return jdbc.query("SELECT state, measures FROM metrics.analysis_evidence WHERE event_id=?",
                (row, i) -> new Evidence(row.getString(1), read(row.getString(2),
                        new TypeReference<Map<String, java.math.BigDecimal>>() {})), eventId)
                .stream().findFirst().orElse(new Evidence("PENDING", Map.of()));
    }
    public void retryEvidence(UUID eventId) {
        int updated=jdbc.update("""
                UPDATE metrics.analysis_evidence SET state='PENDING', attempts=0, last_error=NULL,
                    next_attempt_at=CURRENT_TIMESTAMP WHERE event_id=? AND state<>'COLLECTED'
                """,eventId);
        if (updated!=1) throw new IllegalArgumentException("Collected evidence is immutable or the analysis is missing");
    }
    public Optional<StoredEvaluation> evaluation(UUID id) {
        return jdbc.query(EVALUATION_SELECT + " WHERE e.evaluation_id=?", this::evaluationRow, id).stream().findFirst();
    }
    public List<StoredEvaluation> evaluations(UUID project, long repository, int page, int size) {
        return jdbc.query(EVALUATION_SELECT + " WHERE e.project_id=? AND s.repository_id=? ORDER BY s.analyzed_at DESC, e.evaluated_at DESC, e.evaluation_id LIMIT ? OFFSET ?",
                this::evaluationRow, project, repository, size, (long) page * size);
    }
    public List<PendingEvaluation> pendingEvaluations() {
        return jdbc.query("""
                SELECT c.configuration_id, s.event_id FROM metrics.evaluation_configurations c
                JOIN metrics.analysis_snapshots s ON s.repository_id=c.repository_id AND s.git_ref=c.git_ref
                    AND lower(split_part(s.payload->>'repository','/',1))=lower(c.organization)
                JOIN metrics.analysis_evidence a ON a.event_id=s.event_id
                LEFT JOIN metrics.rubric_evaluations e ON e.configuration_id=c.configuration_id AND e.event_id=s.event_id
                WHERE (c.active AND s.analyzed_at >= c.approved_at AND e.evaluation_id IS NULL
                    OR e.evaluation_id IS NOT NULL AND e.evidence_state <> a.state)
                  AND a.state IN ('COLLECTED','UNAVAILABLE')
                ORDER BY s.received_at LIMIT 20
                """, (row, i) -> new PendingEvaluation(row.getObject(1, UUID.class), row.getObject(2, UUID.class)));
    }
    @Transactional
    public StoredEvaluation evaluateLocked(StoredConfiguration configuration, AnalysisCompletedEvent event,
            Function<List<ManualDecision>, EvaluationSummary> calculate, Evidence evidence) {
        jdbc.update("""
                INSERT INTO metrics.rubric_evaluations(evaluation_id, configuration_id, event_id,
                    project_id, summary, status, evidence_state)
                VALUES (?, ?, ?, ?, '{}', 'PARTIAL', ?) ON CONFLICT (configuration_id,event_id) DO NOTHING
                """, UUID.randomUUID(), configuration.id(), event.eventId(), configuration.projectId(), evidence.state());
        var locked = jdbc.query("""
                SELECT evaluation_id, decisions FROM metrics.rubric_evaluations
                WHERE configuration_id=? AND event_id=? FOR UPDATE
                """, (row, i) -> Map.entry(row.getObject(1, UUID.class),
                this.<List<ManualDecision>>read(row.getString(2), new TypeReference<List<ManualDecision>>() {})),
                configuration.id(), event.eventId()).getFirst();
        var summary = calculate.apply(locked.getValue());
        String status = summary.finalPercentage().isPresent() ? "COMPLETED" : "PARTIAL";
        jdbc.update("""
                UPDATE metrics.rubric_evaluations SET summary=CAST(? AS jsonb), status=?, evidence_state=?
                WHERE evaluation_id=?
                """, write(summary), status, evidence.state(), locked.getKey());
        return new StoredEvaluation(locked.getKey(), configuration.projectId(), configuration.id(), event.eventId(),
                event.repositoryId(), event.gitRef(), event.commitSha(), status, summary, locked.getValue(), evidence.state());
    }
    @Transactional
    public StoredEvaluation reviewLocked(UUID id, Function<StoredEvaluation, StoredEvaluation> review) {
        var previous = jdbc.query(EVALUATION_SELECT + " WHERE e.evaluation_id=? FOR UPDATE OF e",
                this::evaluationRow, id).stream().findFirst().orElseThrow(NoSuchElementException::new);
        var result = review.apply(previous);
        jdbc.update("""
                UPDATE metrics.rubric_evaluations SET summary=CAST(? AS jsonb), decisions=CAST(? AS jsonb),
                    status=?, evidence_state=? WHERE evaluation_id=?
                """, write(result.summary()), write(result.decisions()), result.status(), result.evidenceState(), id);
        return result;
    }
    private StoredEvaluation evaluationRow(ResultSet row, int index) throws SQLException {
        return new StoredEvaluation(row.getObject("evaluation_id", UUID.class), row.getObject("project_id", UUID.class),
                row.getObject("configuration_id", UUID.class), row.getObject("event_id", UUID.class),
                row.getLong("repository_id"), row.getString("git_ref"), row.getString("commit_sha"), row.getString("status"),
                read(row.getString("summary"), EvaluationSummary.class),
                read(row.getString("decisions"), new TypeReference<List<ManualDecision>>() {}), row.getString("evidence_state"));
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (java.io.IOException exception) { throw new IllegalStateException("Cannot serialize evaluation snapshot"); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (java.io.IOException exception) { throw new IllegalStateException("Cannot read evaluation snapshot"); }
    }
    private <T> T read(String value, TypeReference<T> type) {
        try { return json.readValue(value, type); }
        catch (java.io.IOException exception) { throw new IllegalStateException("Cannot read evaluation snapshot"); }
    }
}
