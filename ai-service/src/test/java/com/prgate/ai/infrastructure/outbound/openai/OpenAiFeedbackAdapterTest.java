package com.prgate.ai.infrastructure.outbound.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.ai.application.dto.FeedbackRequest;
import com.prgate.ai.application.port.out.FeedbackGenerationException;
import com.prgate.ai.domain.model.valueobjects.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiFeedbackAdapterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CriterionId criterion = CriterionId.create();
    private final FindingId finding = FindingId.create();
    private MockRestServiceServer server;
    private OpenAiFeedbackAdapter adapter;

    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new OpenAiFeedbackAdapter(builder.build(), mapper, "test-model");
    }

    private FeedbackRequest request() {
        return new FeedbackRequest(List.of(new RubricCriterion(criterion, "Readability", "Clear names")),
                List.of(new TechnicalFinding(finding, criterion, "java:naming", "Main.java", 1, "Unclear name")));
    }

    @Test void mapsStructuredFeedbackAndSendsSchema() throws Exception {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.store").value(false))
                .andRespond(withSuccess(response(finding.value().toString()), MediaType.APPLICATION_JSON));
        var result = adapter.generate(request());
        assertEquals("Review", result.summary());
        assertEquals(finding, result.feedback().getFirst().evidenceIds().iterator().next());
        server.verify();
    }

    @Test void rejectsInventedEvidence() throws Exception {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(response(FindingId.create().value().toString()), MediaType.APPLICATION_JSON));
        assertThrows(FeedbackGenerationException.class, () -> adapter.generate(request()));
    }

    @Test void rejectsIncompleteResponse() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("{\"status\":\"incomplete\",\"output\":[]}", MediaType.APPLICATION_JSON));
        assertThrows(FeedbackGenerationException.class, () -> adapter.generate(request()));
    }

    @Test void hidesProviderErrorBody() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withUnauthorizedRequest().body("private-provider-details"));
        var error = assertThrows(FeedbackGenerationException.class, () -> adapter.generate(request()));
        assertEquals("OpenAI request failed (HTTP 401)", error.getMessage());
    }

    private String response(String evidence) throws Exception {
        String payload = mapper.writeValueAsString(Map.of("summary", "Review", "feedback", List.of(
                Map.of("criterionId", criterion.value().toString(), "evidenceIds", List.of(evidence),
                        "explanation", "Unclear name", "suggestion", "Rename the variable"))));
        return mapper.writeValueAsString(Map.of("status", "completed", "output", List.of(
                Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", payload))))));
    }
}
