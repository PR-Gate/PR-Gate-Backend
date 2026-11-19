package com.prgate.metrics.infrastructure.outbound.sonarqube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.metrics.application.dto.AnalysisCompletedEvent;
import com.prgate.metrics.application.port.out.SonarEvidencePort;
import com.prgate.metrics.application.service.SupportedMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Historical global measures only; never combines a new revision's values with an old task. */
@Component
public class SonarEvidenceHttpAdapter implements SonarEvidencePort {
    private static final DateTimeFormatter SONAR_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssxx").withZone(ZoneOffset.UTC);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String token;
    public SonarEvidenceHttpAdapter(ObjectMapper json,
            @Value("${app.sonar.base-url:http://localhost:9000}") String baseUrl,
            @Value("${app.sonar.token:}") String token) {
        URI uri = URI.create(baseUrl);
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !Set.of("http", "https").contains(uri.getScheme())) throw new IllegalArgumentException("Invalid Sonar URL");
        this.json = json; this.baseUrl = baseUrl.replaceAll("/+$", ""); this.token = token;
    }

    public Map<String, BigDecimal> collect(AnalysisCompletedEvent event) {
        var task = get("/api/ce/task?id=" + encode(event.sonarTaskId())).path("task");
        if (!"SUCCESS".equals(task.path("status").asText())
                || !event.sonarProjectKey().equals(task.path("componentKey").asText())
                || !event.sonarAnalysisId().equals(task.path("analysisId").asText()))
            throw new EvidenceUnavailable("TASK_ANALYSIS_MISMATCH");
        Instant timestamp = event.analyzedAt().truncatedTo(ChronoUnit.SECONDS);
        String interval = "&from=" + encode(SONAR_TIMESTAMP.format(timestamp.minusSeconds(1)))
                + "&to=" + encode(SONAR_TIMESTAMP.format(timestamp.plusSeconds(1)));
        var search = get("/api/project_analyses/search?project=" + encode(event.sonarProjectKey()) + interval + "&ps=100");
        if (!search.path("analyses").isArray() || !search.path("paging").path("total").isIntegralNumber()
                || search.path("paging").path("total").asInt() != search.path("analyses").size())
            throw new EvidenceUnavailable("ANALYSIS_HISTORY_INCOMPLETE");
        int matches = 0;
        boolean verified = false;
        for (var analysis : search.path("analyses")) {
            if (instant(analysis.path("date").asText()).truncatedTo(ChronoUnit.SECONDS).equals(timestamp)) {
                matches++;
                verified |= event.sonarAnalysisId().equals(analysis.path("key").asText())
                        && event.commitSha().equals(analysis.path("revision").asText());
            }
        }
        if (matches != 1 || !verified) throw new EvidenceUnavailable("ANALYSIS_HISTORY_AMBIGUOUS_OR_MISSING");
        var history = get("/api/measures/search_history?component=" + encode(event.sonarProjectKey())
                + "&metrics=" + encode(String.join(",", SupportedMetrics.KEYS)) + interval + "&ps=1000");
        if (!history.path("measures").isArray() || !history.path("paging").path("total").isIntegralNumber()
                || history.path("paging").path("total").asInt() > 1000)
            throw new EvidenceUnavailable("MEASURE_HISTORY_INCOMPLETE");
        Map<String, BigDecimal> result = new HashMap<>();
        for (var metric : history.path("measures")) {
            String key = metric.path("metric").asText();
            if (!SupportedMetrics.KEYS.contains(key)) continue;
            if (!metric.path("history").isArray()) throw new EvidenceUnavailable("MEASURE_HISTORY_INCOMPLETE");
            int observations = 0;
            for (var observation : metric.path("history")) {
                if (instant(observation.path("date").asText()).truncatedTo(ChronoUnit.SECONDS).equals(timestamp)) {
                    if (++observations > 1) throw new EvidenceUnavailable("MEASURE_HISTORY_AMBIGUOUS");
                    if (observation.hasNonNull("value") && !observation.path("value").asText().isBlank()) {
                        BigDecimal value = new BigDecimal(observation.path("value").asText());
                        if (value.signum() < 0 || (Set.of("coverage", "duplicated_lines_density").contains(key)
                                && value.compareTo(BigDecimal.valueOf(100)) > 0))
                            throw new EvidenceUnavailable("INVALID_MEASURE_VALUE");
                        if (result.putIfAbsent(key, value) != null) throw new EvidenceUnavailable("DUPLICATE_METRIC");
                    }
                }
            }
        }
        return Map.copyOf(result);
    }

    public void validateRule(String ruleKey) {
        var rule = get("/api/rules/show?key=" + encode(ruleKey)).path("rule");
        if (!ruleKey.equals(rule.path("key").asText())) throw new IllegalArgumentException("Unknown Sonar rule");
    }
    private JsonNode get(String path) {
        if (token.isBlank()) throw new IllegalStateException("Sonar read credentials are not configured");
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + token).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Sonar evidence lookup unavailable");
            return json.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Sonar evidence lookup interrupted");
        } catch (java.io.IOException exception) { throw new IllegalStateException("Invalid Sonar evidence response"); }
    }
    private static Instant instant(String value) {
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (java.time.format.DateTimeParseException exception) {
            return OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")).toInstant();
        }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
