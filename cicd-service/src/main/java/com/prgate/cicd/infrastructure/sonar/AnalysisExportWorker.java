package com.prgate.cicd.infrastructure.sonar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Converts durable, correlated receipts into an outbox; never calls Metrics directly. */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class AnalysisExportWorker {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SonarProjectHttpAdapter sonar;
    private final CompletedAnalysisEventFactory events;
    private final TransactionTemplate transaction;

    public AnalysisExportWorker(JdbcTemplate jdbc, ObjectMapper json, SonarProjectHttpAdapter sonar,
                               CompletedAnalysisEventFactory events, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.json = json;
        this.sonar = sonar;
        this.events = events;
        this.transaction = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString = "${app.messaging.export-delay-ms:10000}")
    public void stagePending() {
        jdbc.update("""
                INSERT INTO cicd.analysis_export_receipts (source_hash)
                SELECT DISTINCT sonar_receipt_hash FROM cicd.push_analysis_receipts
                WHERE analysis_status = 'SUCCESS' AND sonar_receipt_hash IS NOT NULL
                ON CONFLICT (source_hash) DO NOTHING
                """);
        var entries = jdbc.query("""
                SELECT DISTINCT ON (e.source_hash) e.source_hash, s.payload,
                    p.repository_id, p.owner_login || '/' || p.repository_name AS repository,
                    p.git_ref, p.before_sha, p.after_sha
                FROM cicd.analysis_export_receipts e
                JOIN cicd.push_analysis_receipts p ON p.sonar_receipt_hash = e.source_hash
                JOIN cicd.sonar_webhook_receipts s ON s.payload_hash = e.source_hash
                WHERE e.exported_event_id IS NULL AND e.attempts < 20
                  AND e.next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY e.source_hash LIMIT 10
                """, (row, index) -> new Entry(row.getString("source_hash"), row.getString("payload"),
                new CompletedAnalysisEventFactory.Source(row.getLong("repository_id"),
                        row.getString("repository"), row.getString("git_ref"),
                        row.getString("before_sha"), row.getString("after_sha"))));
        for (var entry : entries) stage(entry);
    }

    private void stage(Entry entry) {
        try {
            var webhook = json.readTree(entry.payload());
            var verified = sonar.verifyCompletedAnalysis(webhook.path("taskId").asText(),
                    webhook.path("project").path("key").asText(), entry.source().revision());
            var event = events.build(entry.source(), webhook, verified);
            transaction.executeWithoutResult(ignored -> {
                jdbc.update("""
                        INSERT INTO cicd.analysis_event_outbox (event_id, payload)
                        VALUES (?, ?) ON CONFLICT (event_id) DO NOTHING
                        """, event.id(), event.payload());
                jdbc.update("""
                        UPDATE cicd.analysis_export_receipts SET exported_event_id = ?, last_error = NULL
                        WHERE source_hash = ?
                        """, event.id(), entry.hash());
            });
        } catch (Exception exception) {
            jdbc.update("""
                    UPDATE cicd.analysis_export_receipts SET attempts = attempts + 1,
                        next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '30 seconds', last_error = ?
                    WHERE source_hash = ? AND exported_event_id IS NULL
                    """, exception.getClass().getSimpleName(), entry.hash());
        }
    }

    private record Entry(String hash, String payload, CompletedAnalysisEventFactory.Source source) { }
}
