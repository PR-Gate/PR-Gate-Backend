package com.prgate.cicd.infrastructure.sonar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompletedAnalysisEventFactoryTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CompletedAnalysisEventFactory factory = new CompletedAnalysisEventFactory(json);
    private final CompletedAnalysisEventFactory.Source source = new CompletedAnalysisEventFactory.Source(
            123, "example/backend", "refs/heads/main", "a".repeat(40), "b".repeat(40));

    @Test void retriesHaveStableIdentityAndFailedGateRemainsAnAnalysisResult() throws Exception {
        var webhook = webhook();
        var verified = new SonarProjectHttpAdapter.VerifiedAnalysis("analysis-123", "ERROR");
        var first = factory.build(source, webhook, verified);
        var retry = factory.build(source, webhook, verified);
        assertEquals(first, retry);
        var payload = json.readTree(first.payload());
        assertEquals("ERROR", payload.path("qualityGateStatus").asText());
        assertEquals("analysis-123", payload.path("sonarAnalysisId").asText());
        assertEquals(0, payload.path("measures").size());
        assertEquals("2026-11-17T00:49:03Z", payload.path("analyzedAt").asText());
    }

    @Test void distinctAttemptsStaySeparateAndRevisionMismatchIsRejected() throws Exception {
        var webhook = webhook();
        var verified = new SonarProjectHttpAdapter.VerifiedAnalysis("analysis-123", "OK");
        var first = factory.build(source, webhook, verified);
        ((com.fasterxml.jackson.databind.node.ObjectNode) webhook.path("properties"))
                .put("sonar.analysis.prgateWorkflowRunAttempt", "2");
        assertNotEquals(first.id(), factory.build(source, webhook, verified).id());
        ((com.fasterxml.jackson.databind.node.ObjectNode) webhook).put("revision", "c".repeat(40));
        assertThrows(IllegalArgumentException.class, () -> factory.build(source, webhook, verified));
    }

    private com.fasterxml.jackson.databind.JsonNode webhook() throws Exception {
        return json.readTree("""
                {"project":{"key":"example-backend"},"taskId":"task-123",
                 "revision":"%s","analysedAt":"2026-11-16T19:49:03-0500",
                 "properties":{"sonar.analysis.prgateWorkflowRunId":"456",
                   "sonar.analysis.prgateWorkflowRunAttempt":"1",
                   "sonar.analysis.prgateRepositoryId":"123",
                   "sonar.analysis.prgatePushRef":"refs/heads/main",
                   "sonar.analysis.prgatePushBefore":"%s"}}
                """.formatted("b".repeat(40), "a".repeat(40)));
    }
}
