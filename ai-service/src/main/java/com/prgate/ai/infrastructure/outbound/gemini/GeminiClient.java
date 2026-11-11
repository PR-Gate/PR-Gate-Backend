package com.prgate.ai.infrastructure.outbound.gemini;

import com.fasterxml.jackson.databind.*;
import com.prgate.ai.application.port.out.AiGenerationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.net.http.HttpClient;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.util.*;
import static com.prgate.ai.application.port.out.AiGenerationException.Provider.GEMINI;
import static com.prgate.ai.application.port.out.AiGenerationException.Reason.*;

@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "gemini")
public final class GeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String model;

    public GeminiClient(RestClient.Builder builder, ObjectMapper mapper,
            @Value("${gemini.api-key:}") String key,
            @Value("${gemini.model:}") String model,
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl) {
        if (key == null || key.isBlank() || model == null || model.isBlank())
            throw new IllegalArgumentException("Configure GEMINI_API_KEY and GEMINI_MODEL");
        String normalized = model.strip().replaceFirst("^models/", "");
        if (!normalized.matches("[A-Za-z0-9._-]+"))
            throw new IllegalArgumentException("GEMINI_MODEL must be a model identifier");
        this.model = normalized;
        this.mapper = mapper;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("x-goog-api-key", key.strip()).build();
    }

    public JsonNode generate(String instructions, Object input, Map<String, Object> schema, int maxTokens) {
        long started = System.nanoTime();
        try {
            var body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instructions))),
                "contents", List.of(Map.of("role", "user",
                        "parts", List.of(Map.of("text", mapper.writeValueAsString(input))))),
                "generationConfig", Map.of("candidateCount", 1, "maxOutputTokens", maxTokens,
                        "responseMimeType", "application/json", "responseJsonSchema", schema));
            JsonNode response = client.post().uri("/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            if (response == null) throw failure(RESPONSE_INVALID);
            String blocked = response.path("promptFeedback").path("blockReason").asText("");
            if (!blocked.isBlank() && !"BLOCK_REASON_UNSPECIFIED".equals(blocked)) throw failure(RESPONSE_BLOCKED);
            var candidates = response.path("candidates");
            if (!candidates.isArray() || candidates.size() != 1) throw failure(RESPONSE_INVALID);
            var candidate = candidates.get(0);
            String finish = candidate.path("finishReason").asText("");
            if (Set.of("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII").contains(finish))
                throw failure(RESPONSE_BLOCKED);
            if (!"STOP".equals(finish)) throw failure(RESPONSE_INCOMPLETE);
            var text = new StringBuilder();
            for (var part : candidate.path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false) && part.path("text").isTextual())
                    text.append(part.path("text").asText());
            }
            if (text.isEmpty()) throw failure(RESPONSE_INVALID);
            var result = mapper.readTree(text.toString());
            if (result == null || !result.isObject()) throw failure(RESPONSE_INVALID);
            return result;
        } catch (AiGenerationException ex) { throw ex; }
        catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            boolean invalidKey = false;
            try {
                var error = mapper.readTree(ex.getResponseBodyAsString()).path("error");
                for (var detail : error.path("details"))
                    if ("API_KEY_INVALID".equals(detail.path("reason").asText())) invalidKey = true;
            } catch (Exception ignored) { /* Raw error bodies never leave this adapter. */ }
            var reason = invalidKey || status == 401 ? AUTHENTICATION_FAILED
                    : status == 403 ? PERMISSION_DENIED : status == 404 ? MODEL_UNAVAILABLE
                    : status == 429 ? QUOTA_EXCEEDED : status == 400 ? REQUEST_REJECTED : UNAVAILABLE;
            throw new AiGenerationException(GEMINI, reason, status);
        } catch (ResourceAccessException ex) {
            // Fixed categories only; exception messages may contain sensitive data.
            log.warn("Gemini connection failed: category={}, elapsedMs={}",
                    connectionCategory(ex), Duration.ofNanos(System.nanoTime() - started).toMillis());
            throw failure(CONNECTION_FAILED);
        }
        catch (RestClientException ex) { throw failure(UNAVAILABLE); }
        catch (Exception ex) { throw failure(RESPONSE_INVALID); }
    }
    public <T> T decode(JsonNode value, Class<T> type) {
        try { return mapper.treeToValue(value, type); }
        catch (Exception ex) { throw failure(RESPONSE_INVALID); }
    }
    static String connectionCategory(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        String category = "NETWORK_IO";
        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof UnknownHostException) return "DNS";
            if (cause instanceof SSLException) return "TLS_CERTIFICATE_OR_HANDSHAKE";
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException)
                category = "TIMEOUT";
            else if (cause instanceof ConnectException && "NETWORK_IO".equals(category))
                category = "CONNECTION_FAILED";
        }
        return category;
    }
    private static AiGenerationException failure(AiGenerationException.Reason reason) {
        return new AiGenerationException(GEMINI, reason);
    }
}
