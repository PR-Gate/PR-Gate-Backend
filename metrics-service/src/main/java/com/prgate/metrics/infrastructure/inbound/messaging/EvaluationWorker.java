package com.prgate.metrics.infrastructure.inbound.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.metrics.application.dto.AnalysisCompletedEvent;
import com.prgate.metrics.application.port.out.*;
import com.prgate.metrics.application.service.RubricEvaluationService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;

@Configuration
@EnableScheduling
public class EvaluationWorker {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SonarEvidencePort sonar;
    private final EvaluationStore evaluations;
    private final RubricEvaluationService service;
    public EvaluationWorker(JdbcTemplate jdbc, ObjectMapper json, SonarEvidencePort sonar,
                            EvaluationStore evaluations, RubricEvaluationService service) {
        this.jdbc = jdbc; this.json = json; this.sonar = sonar; this.evaluations = evaluations; this.service = service;
    }
    @Scheduled(fixedDelayString = "${app.evaluation.delay-ms:10000}")
    public void collectAndEvaluate() {
        var entries = jdbc.query("""
                SELECT a.event_id, s.payload FROM metrics.analysis_evidence a
                JOIN metrics.analysis_snapshots s ON s.event_id=a.event_id
                WHERE a.state='PENDING' AND a.next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY s.received_at LIMIT 10
                """, (row, i) -> new Pending(row.getObject(1, UUID.class), row.getString(2)));
        for (var entry : entries) collect(entry);
        for (var pending : evaluations.pendingEvaluations()) {
            evaluations.configuration(pending.configurationId())
                    .ifPresent(configuration -> service.evaluateTrusted(configuration, pending.eventId()));
        }
    }
    private void collect(Pending entry) {
        try {
            var event = json.readValue(entry.payload(), AnalysisCompletedEvent.class);
            var measures = sonar.collect(event);
            jdbc.update("""
                    UPDATE metrics.analysis_evidence SET state='COLLECTED', measures=CAST(? AS jsonb),
                        collected_at=CURRENT_TIMESTAMP, last_error=NULL WHERE event_id=? AND state='PENDING'
                    """, json.writeValueAsString(measures), entry.id());
        } catch (SonarEvidencePort.EvidenceUnavailable exception) {
            jdbc.update("""
                    UPDATE metrics.analysis_evidence SET state='UNAVAILABLE', last_error=?
                    WHERE event_id=? AND state='PENDING'
                    """, exception.getMessage(), entry.id());
        } catch (Exception exception) {
            jdbc.update("""
                    UPDATE metrics.analysis_evidence SET attempts=attempts+1,
                        state=CASE WHEN attempts>=19 THEN 'UNAVAILABLE' ELSE 'PENDING' END,
                        next_attempt_at=CURRENT_TIMESTAMP + INTERVAL '30 seconds', last_error=?
                    WHERE event_id=? AND state='PENDING'
                    """, exception.getClass().getSimpleName(), entry.id());
        }
    }
    private record Pending(UUID id, String payload) { }
}
