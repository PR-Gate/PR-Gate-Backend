package com.prgate.ai.infrastructure.outbound.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.ai.application.dto.RubricTranslationRequest;
import com.prgate.ai.application.port.out.RubricTranslationException;
import com.prgate.ai.domain.model.valueobjects.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.web.client.RestClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import static com.prgate.ai.application.port.out.RubricTranslationException.Reason.*;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiRubricErrorTest {
    private HttpServer server;
    private OpenAiRubricAdapter adapter;
    private int status;
    private String response;
    private final CriterionId criterion = CriterionId.create();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/responses", exchange -> {
            exchange.getRequestBody().readAllBytes();
            var bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        adapter = new OpenAiRubricAdapter(RestClient.builder(), new ObjectMapper(),
                "fixture-only", "fixture-model", "http://localhost:" + server.getAddress().getPort());
    }
    @AfterEach void stop() { server.stop(0); }

    static Stream<Arguments> failures() {
        return Stream.of(
            Arguments.of(429, "credit_balance_exhausted", CREDITS_EXHAUSTED),
            Arguments.of(429, "insufficient_quota", USAGE_LIMIT),
            Arguments.of(429, "project_spend_limit_exceeded", USAGE_LIMIT),
            Arguments.of(429, "rate_limit_exceeded", RATE_LIMIT),
            Arguments.of(404, "model_not_found", MODEL),
            Arguments.of(401, "invalid_api_key", AUTHENTICATION),
            Arguments.of(403, "permission_denied", PERMISSION),
            Arguments.of(400, "invalid_json_schema", INVALID_REQUEST),
            Arguments.of(503, "server_error", GENERAL)
        );
    }

    @ParameterizedTest @MethodSource("failures")
    void translatesHttpErrorsWithoutLeakingRawBody(int code, String providerCode,
                                                   RubricTranslationException.Reason reason) {
        status = code;
        response = "{\"error\":{\"code\":\"" + providerCode + "\",\"message\":\"private-provider-value\"}}";
        var failure = assertThrows(RubricTranslationException.class, () -> adapter.translate(request()));
        assertEquals(reason, failure.reason());
        assertEquals(Integer.valueOf(code), failure.upstreamStatus());
        assertFalse(failure.getMessage().contains("private-provider-value"));
    }

    @Test void handlesMalformedErrorBodyWithoutLosingStatus() {
        status = 429;
        response = "<html>private-provider-value</html>";
        var failure = assertThrows(RubricTranslationException.class, () -> adapter.translate(request()));
        assertEquals(RATE_LIMIT, failure.reason());
        assertEquals(Integer.valueOf(429), failure.upstreamStatus());
    }

    @Test void keepsSuccessfulStructuredResponseMapping() {
        status = 200;
        response = """
            {"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":
            "{\\"checks\\":[{\\"criterionId\\":\\"%s\\",\\"mechanism\\":\\"MANUAL_REVIEW\\",\\"target\\":\\"manual-review\\",\\"rationale\\":\\"Review manually\\"}]}"}]}]}
            """.formatted(criterion.value());
        var result = adapter.translate(request());
        assertEquals(1, result.checks().size());
        assertEquals(criterion, result.checks().getFirst().criterionId());
    }

    @Test void distinguishesIncompleteAndInvalidResponses() {
        status = 200;
        response = "{\"status\":\"incomplete\",\"output\":[]}";
        var incomplete = assertThrows(RubricTranslationException.class, () -> adapter.translate(request()));
        assertEquals(INCOMPLETE, incomplete.reason());
        response = "{\"status\":\"completed\",\"output\":[]}";
        var invalid = assertThrows(RubricTranslationException.class, () -> adapter.translate(request()));
        assertEquals(INVALID_RESPONSE, invalid.reason());
    }

    private RubricTranslationRequest request() {
        return new RubricTranslationRequest(new RubricReference("r", 1), new SourceLanguage("java"),
                List.of(new RubricCriterion(criterion, "Naming", "Understand intent")));
    }
}