package com.prgate.ai.integration;

import com.fasterxml.jackson.databind.*;
import com.prgate.ai.domain.model.enums.*;
import com.prgate.ai.domain.model.valueobjects.*;
import com.prgate.ai.domain.repository.*;
import com.prgate.ai.infrastructure.outbound.persistence.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/** Real Spring + HTTP + Flyway + SQL. Only external HTTP services have deterministic fixtures. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:api-integration;DB_CLOSE_DELAY=-1",
        "ai.provider=openai", "openai.api-key=integration-only", "openai.model=integration-model",
        "prgate.internal.service-token=integration-service"
})
class AiApiIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicBoolean FAIL_FEEDBACK = new AtomicBoolean(false);
    private static final HttpServer UPSTREAM = startUpstream();

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TechnicalAssessmentPlanRepository plans;
    @Autowired AssessmentRepository assessments;

    @DynamicPropertySource static void urls(DynamicPropertyRegistry properties) {
        properties.add("openai.base-url", () -> "http://localhost:" + UPSTREAM.getAddress().getPort() + "/v1");
        properties.add("sonar.base-url", () -> "http://localhost:" + UPSTREAM.getAddress().getPort());
        properties.add("sonar.token", () -> "sonar-fixture");
    }
    @AfterAll static void stop() { UPSTREAM.stop(0); }

    private static HttpServer startUpstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/rules/search", exchange -> {
                byte[] body = "{\"paging\":{\"total\":1},\"rules\":[{\"key\":\"java:fixture\",\"lang\":\"java\",\"status\":\"READY\"}]}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.createContext("/v1/responses", exchange -> {
                try {
                    var request = JSON.readTree(exchange.getRequestBody());
                    assertEquals("Bearer integration-only", exchange.getRequestHeaders().getFirst("Authorization"));
                    assertFalse(request.path("store").asBoolean(true));
                    assertTrue(request.at("/text/format/strict").asBoolean());
                    var input = JSON.readTree(request.path("input").asText());
                    boolean feedback = request.at("/text/format/name").asText().equals("assessment_feedback");
                    if (feedback && FAIL_FEEDBACK.get()) {
                        byte[] bytes = "{\"error\":\"private-upstream-value\"}".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(503, bytes.length);
                        exchange.getResponseBody().write(bytes);
                        return;
                    }
                    String criterion = input.at("/criteria/0/id/value").asText();
                    Object result = feedback
                        ? Map.of("summary", "Improve naming", "feedback", List.of(Map.of(
                            "criterionId", criterion, "evidenceIds", List.of(input.at("/findings/0/id/value").asText()),
                            "explanation", "The name hides intent", "suggestion", "Use a descriptive name")))
                        : Map.of("checks", List.of(Map.of("criterionId", criterion, "mechanism", "SONAR_RULE",
                            "target", "java:fixture", "rationale", "Check naming")));
                    var envelope = Map.of("status", "completed", "output", List.of(Map.of("type", "message",
                            "content", List.of(Map.of("type", "output_text", "text", JSON.writeValueAsString(result))))));
                    byte[] body = JSON.writeValueAsBytes(envelope);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } finally { exchange.close(); }
            });
            server.start();
            return server;
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }

    private ResponseEntity<JsonNode> call(HttpMethod method, String uri, Object body) {
        var headers = new HttpHeaders();
        headers.setBearerAuth("integration-service");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(uri, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
    private JsonNode createAndApprovePlan(UUID criterion) {
        var body = Map.of("rubricId", "r-1", "rubricVersion", 1, "language", "java", "criteria",
                List.of(Map.of("id", criterion.toString(), "description", "Naming", "objective", "Express intent")));
        var created = call(HttpMethod.POST, "/api/technical-plans", body);
        assertEquals(201, created.getStatusCode().value(), String.valueOf(created.getBody()));
        var plan = created.getBody();
        assertEquals("DRAFT", plan.path("status").asText());
        assertTrue(plan.path("id").isTextual());
        var approval = call(HttpMethod.POST, "/api/technical-plans/" + plan.path("id").asText() + "/approval", null);
        assertEquals(200, approval.getStatusCode().value(), String.valueOf(approval.getBody()));
        return approval.getBody();
    }
    private JsonNode recordAnalysis(JsonNode plan, UUID criterion) {
        var body = Map.of("planId", plan.path("id").asText(), "studentId", "student-1",
                "submissionId", UUID.randomUUID().toString(), "revision", "commit-1", "analysisId", "analysis-1",
                "findings", List.of(Map.of("id", UUID.randomUUID().toString(), "criterionId", criterion.toString(),
                        "ruleKey", "java:fixture", "filePath", "Main.java", "line", 1, "message", "Unclear name")));
        var response = call(HttpMethod.POST, "/api/assessments", body);
        assertEquals(201, response.getStatusCode().value(), String.valueOf(response.getBody()));
        return response.getBody();
    }

    @Test void realHttpFlowPersistsAndRehydratesPlanAnalysisAndFeedback() {
        var criterion = UUID.randomUUID();
        var plan = createAndApprovePlan(criterion);
        assertEquals("APPROVED", plan.path("status").asText());
        assertTrue(plan.path("catalogVersion").asText().startsWith("sonar-rules-"));
        var analysis = recordAnalysis(plan, criterion);
        var path = "/api/assessments/" + analysis.path("id").asText();
        var feedback = call(HttpMethod.POST, path + "/feedback", null);
        assertEquals(200, feedback.getStatusCode().value(), String.valueOf(feedback.getBody()));
        assertEquals("Improve naming", feedback.getBody().path("summary").asText());
        var retrieved = call(HttpMethod.GET, path, null);
        assertEquals("COMPLETED", retrieved.getBody().path("status").asText());
        // New repository instances reload from SQL, not from a shared in-memory aggregate.
        var reloadedPlans = new JdbcTechnicalPlanRepository(jdbc, mapper);
        var planId = new PlanId(UUID.fromString(plan.path("id").asText()));
        assertEquals(PlanStatus.APPROVED, reloadedPlans.findById(planId).orElseThrow().status());
        var reloadedAssessments = new JdbcAssessmentRepository(jdbc, mapper, reloadedPlans);
        var assessmentId = new AssessmentId(UUID.fromString(analysis.path("id").asText()));
        var reloaded = reloadedAssessments.findById(assessmentId).orElseThrow();
        assertEquals(AssessmentStatus.COMPLETED, reloaded.status());
        assertEquals(1, reloaded.feedback().size());
    }

    @Test void upstreamFailureIsPersistedAndRetryUsesOriginalAnalysis() {
        var criterion = UUID.randomUUID();
        var plan = createAndApprovePlan(criterion);
        var assessment = recordAnalysis(plan, criterion);
        var path = "/api/assessments/" + assessment.path("id").asText();
        FAIL_FEEDBACK.set(true);
        try {
            var failed = call(HttpMethod.POST, path + "/feedback", null);
            assertEquals(502, failed.getStatusCode().value());
            assertFalse(failed.getBody().toString().contains("private-upstream-value"));
            var reloaded = call(HttpMethod.GET, path, null);
            assertEquals("FAILED", reloaded.getBody().path("status").asText());
            assertEquals("analysis-1", reloaded.getBody().path("analysisId").asText());
        } finally { FAIL_FEEDBACK.set(false); }
        var retry = call(HttpMethod.POST, path + "/feedback/retry", null);
        assertEquals(200, retry.getStatusCode().value(), String.valueOf(retry.getBody()));
        assertEquals("COMPLETED", call(HttpMethod.GET, path, null).getBody().path("status").asText());
    }

    @Test void sqlRejectsStaleWrites() {
        var plan = createAndApprovePlan(UUID.randomUUID());
        var id = new PlanId(UUID.fromString(plan.path("id").asText()));
        var first = plans.findById(id).orElseThrow();
        var stale = plans.findById(id).orElseThrow();
        plans.save(first);
        assertThrows(OptimisticLockingFailureException.class, () -> plans.save(stale));
    }

    @Test void rejectsAnonymousInvalidAndUnknownRequests() {
        assertEquals(401, http.getForEntity("/api/technical-plans/" + UUID.randomUUID(), String.class)
                .getStatusCode().value());
        assertEquals(400, call(HttpMethod.POST, "/api/technical-plans",
                Map.of("rubricId", "", "rubricVersion", 0, "language", "java", "criteria", List.of()))
                .getStatusCode().value());
        assertEquals(404, call(HttpMethod.GET, "/api/technical-plans/" + UUID.randomUUID(), null)
                .getStatusCode().value());

        assertEquals(400, call(HttpMethod.GET, "/api/technical-plans/not-a-uuid", null)
                .getStatusCode().value());
    }
}