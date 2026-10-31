package com.prgate.cicd.infrastructure.sonar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.cicd.application.model.RecordSonarAnalysisCommand;
import com.prgate.cicd.application.port.in.RecordSonarAnalysisUseCase;
import com.prgate.cicd.domain.model.enums.QualityGateStatus;
import com.prgate.cicd.domain.model.valueobject.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;

@Configuration
@EnableScheduling
public class SonarWebhookInboxWorker {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SonarProjectHttpAdapter sonar;
    private final RecordSonarAnalysisUseCase recorder;
    public SonarWebhookInboxWorker(JdbcTemplate jdbc, ObjectMapper json,
                                    SonarProjectHttpAdapter sonar, RecordSonarAnalysisUseCase recorder) {
        this.jdbc = jdbc; this.json = json; this.sonar = sonar; this.recorder = recorder;
    }

    @Scheduled(fixedDelayString = "${app.sonar.inbox-delay-ms:10000}")
    public void processPending() {
        var entries = jdbc.query("""
                SELECT payload_hash, payload FROM cicd.sonar_webhook_inbox
                WHERE processed_at IS NULL AND attempts < 20 AND next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY received_at LIMIT 10
                """, (row, index) -> new Entry(row.getString(1), row.getString(2)));
        for (var entry : entries) {
            try {
                recorder.record(toCommand(json.readTree(entry.payload())));
                jdbc.update("UPDATE cicd.sonar_webhook_inbox SET processed_at=CURRENT_TIMESTAMP, last_error=NULL WHERE payload_hash=?",
                        entry.hash());
            } catch (Exception exception) {
                // Persist only the type; remote bodies and secrets must not enter logs or database error fields.
                jdbc.update("""
                        UPDATE cicd.sonar_webhook_inbox SET attempts=attempts+1,
                        next_attempt_at=CURRENT_TIMESTAMP + INTERVAL '30 seconds', last_error=?
                        WHERE payload_hash=? AND processed_at IS NULL
                        """, exception.getClass().getSimpleName(), entry.hash());
            }
        }
    }

    private RecordSonarAnalysisCommand toCommand(JsonNode payload) {
        var properties = payload.path("properties");
        String projectKey = text(payload.path("project"), "key");
        String analysisId = sonar.resolveAnalysisId(text(payload, "taskId"), projectKey);
        var conditions = payload.path("qualityGate").path("conditions");
        BigDecimal vulnerabilities = metric(conditions, "vulnerabilities");
        var metrics = new SonarMetrics(metric(conditions, "coverage"),
                vulnerabilities == null ? null : vulnerabilities.intValueExact(),
                metric(conditions, "duplicated_lines_density"), metric(conditions, "sqale_debt_ratio"));
        var analysis = new SonarAnalysisResult(analysisId,
                QualityGateStatus.valueOf(text(payload.path("qualityGate"), "status")), metrics,
                URI.create(text(payload.path("project"), "url")),
                parseTimestamp(text(payload, "analysedAt")));
        String branch = text(properties, "sonar.analysis.prgateBranch");
        // Sonar's logical main-branch label may differ from the Git ref analyzed.
        // The trusted workflow supplies the Git ref; the application also verifies its commit and attempt.
        if (payload.path("branch").has("isMain") && !payload.path("branch").path("isMain").asBoolean()) {
            throw new IllegalArgumentException("Community Build expects its main analysis branch");
        }
        return new RecordSonarAnalysisCommand(Long.parseLong(text(properties, "sonar.analysis.prgateRepositoryId")),
                new GitHubWorkflowRunId(Long.parseLong(text(properties, "sonar.analysis.prgateRunId"))),
                new GitHubRunAttempt(Integer.parseInt(text(properties, "sonar.analysis.prgateRunAttempt"))),
                new SonarProjectKey(projectKey), branch, new GitCommitSha(text(payload, "revision")), analysis);
    }

    private static String text(JsonNode node, String key) {
        var value = node.path(key);
        if (!value.isTextual() || value.asText().isBlank()) { throw new IllegalArgumentException("Missing Sonar field: " + key); }
        return value.asText();
    }

    private static java.time.Instant parseTimestamp(String value) {
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (java.time.format.DateTimeParseException exception) {
            return OffsetDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")).toInstant();
        }
    }

    private static BigDecimal metric(JsonNode conditions, String key) {
        for (var condition : conditions) {
            if (key.equals(condition.path("metric").asText()) && condition.hasNonNull("value")) {
                return new BigDecimal(condition.path("value").asText());
            }
        }
        return null;
    }
    private record Entry(String hash, String payload) { }
}
