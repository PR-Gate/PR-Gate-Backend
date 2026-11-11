package com.prgate.ai.infrastructure.outbound.gemini;

import com.fasterxml.jackson.databind.*;
import com.prgate.ai.application.dto.*;
import com.prgate.ai.application.port.out.AiGenerationException;
import com.prgate.ai.domain.model.valueobjects.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GeminiAdaptersTest {
    private HttpServer server;
    private GeminiClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private JsonNode sent;
    private String header;
    private String path;
    private int status = 200;
    private String response;
    private final CriterionId criterion = CriterionId.create();
    private final FindingId finding = FindingId.create();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            path = exchange.getRequestURI().getPath();
            header = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            sent = mapper.readTree(exchange.getRequestBody());
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new GeminiClient(RestClient.builder(), mapper, "fixture-key", "fixture-model",
                "http://localhost:" + server.getAddress().getPort());
    }
    @AfterEach void stop() { server.stop(0); }

    private RubricTranslationRequest rubric() {
        return new RubricTranslationRequest(new RubricReference("r", 1), new SourceLanguage("java"),
                List.of(new RubricCriterion(criterion, "Readable names", "Express intent")));
    }
    private FeedbackRequest feedbackRequest() {
        return new FeedbackRequest(rubric().criteria(), List.of(new TechnicalFinding(
                finding, criterion, "java:fixture", "Main.java", 1, "Unclear name")));
    }
    private void completed(Object output) throws Exception {
        response = mapper.writeValueAsString(Map.of("candidates", List.of(Map.of(
                "finishReason", "STOP", "content", Map.of("parts", List.of(
                    Map.of("text", "ignored private thought", "thought", true),
                    Map.of("text", mapper.writeValueAsString(output))))))));
    }
    private Map<String, Object> check(CriterionId id) {
        return Map.of("criterionId", id.value().toString(), "mechanism", "MANUAL_REVIEW",
                "target", "manual-review", "rationale", "Needs human verification");
    }
    @Test void sendsNativeSchemaAndMapsRubric() throws Exception {
        completed(Map.of("checks", List.of(check(criterion))));
        var result = new GeminiRubricAdapter(client).translate(rubric());
        assertEquals(criterion, result.checks().getFirst().criterionId());
        assertEquals("fixture-key", header);
        assertEquals("/models/fixture-model:generateContent", path);
        assertEquals("application/json", sent.at("/generationConfig/responseMimeType").asText());
        assertTrue(sent.at("/generationConfig/responseJsonSchema").isObject());
        assertFalse(sent.has("store"));
        assertTrue(sent.at("/systemInstruction/parts/0/text").asText().contains("UUID"));
        var input = mapper.readTree(sent.at("/contents/0/parts/0/text").asText());
        assertEquals(criterion.value().toString(), input.at("/criteria/0/id").asText());
    }
    @Test void rejectsForeignCriteria() throws Exception {
        completed(Map.of("checks", List.of(check(CriterionId.create()))));
        var error = assertThrows(AiGenerationException.class, () -> new GeminiRubricAdapter(client).translate(rubric()));
        assertEquals("GEMINI_RESPONSE_INVALID", error.code());
    }
    @Test void mapsFeedbackAndRejectsInventedEvidence() throws Exception {
        var adapter = new GeminiFeedbackAdapter(client);
        completed(Map.of("summary", "Naming", "feedback", List.of(Map.of(
                "criterionId", criterion.value().toString(), "evidenceIds", List.of(finding.value().toString()),
                "explanation", "Name is unclear", "suggestion", "Use an explicit name"))));
        assertEquals(finding, adapter.generate(feedbackRequest()).feedback().getFirst().evidenceIds().iterator().next());
        completed(Map.of("summary", "Naming", "feedback", List.of(Map.of(
                "criterionId", criterion.value().toString(), "evidenceIds", List.of(UUID.randomUUID().toString()),
                "explanation", "Invented", "suggestion", "Rename"))));
        assertThrows(AiGenerationException.class, () -> adapter.generate(feedbackRequest()));
    }
    @Test void acceptsEmptyFeedbackForNoFindings() throws Exception {
        completed(Map.of("summary", "No findings supplied", "feedback", List.of()));
        var result = new GeminiFeedbackAdapter(client).generate(new FeedbackRequest(rubric().criteria(), List.of()));
        assertTrue(result.feedback().isEmpty());
    }
    @Test void quotaFailureIsSafeAndSpecific() {
        status = 429;
        response = "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"private-provider-value\"}}";
        var error = assertThrows(AiGenerationException.class, () -> new GeminiRubricAdapter(client).translate(rubric()));
        assertEquals("GEMINI_QUOTA_EXCEEDED", error.code());
        assertEquals(Integer.valueOf(429), error.upstreamStatus());
        assertFalse(error.getMessage().contains("private-provider-value"));
    }
    @Test void recognizesInvalidKeyEvenWhenProviderUses400() {
        status = 400;
        response = "{\"error\":{\"details\":[{\"reason\":\"API_KEY_INVALID\"}],\"message\":\"private-value\"}}";
        var error = assertThrows(AiGenerationException.class, () -> new GeminiRubricAdapter(client).translate(rubric()));
        assertEquals("GEMINI_AUTHENTICATION_FAILED", error.code());
    }
    @Test void rejectsBlockedAndTruncatedResponses() {
        response = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";
        var blocked = assertThrows(AiGenerationException.class, () -> new GeminiRubricAdapter(client).translate(rubric()));
        assertEquals("GEMINI_RESPONSE_BLOCKED", blocked.code());
        response = "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"{}\"}]}}]}";
        var incomplete = assertThrows(AiGenerationException.class, () -> new GeminiRubricAdapter(client).translate(rubric()));
        assertEquals("GEMINI_RESPONSE_INCOMPLETE", incomplete.code());
    }
    @Test void rejectsNonJsonContent() {
        response = "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"not JSON\"}]}}]}";
        var error = assertThrows(AiGenerationException.class, () -> new GeminiRubricAdapter(client).translate(rubric()));
        assertEquals("GEMINI_RESPONSE_INVALID", error.code());
    }
}