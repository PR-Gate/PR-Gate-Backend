package com.prgate.metrics.infrastructure.outbound.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.metrics.application.dto.GradeEstimationEvidence;
import com.prgate.metrics.application.port.out.GradeEstimatorPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

@Component
public class AiGradeEstimatorHttpAdapter implements GradeEstimatorPort {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final String baseUrl,token;
    public AiGradeEstimatorHttpAdapter(ObjectMapper json,@Value("${app.ai.base-url:http://localhost:8081}") String baseUrl,
                                        @Value("${app.ai.token:}") String token) {
        URI uri=URI.create(baseUrl);
        if (uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || !java.util.Set.of("http","https").contains(uri.getScheme())) throw new IllegalArgumentException("Invalid AI URL");
        this.json=json; this.baseUrl=baseUrl.replaceAll("/+$",""); this.token=token;
    }
    public String estimate(GradeEstimationEvidence evidence) {
        if (token.isBlank()) throw new IllegalStateException("AI backend credentials are not configured");
        try {
            var request=HttpRequest.newBuilder(URI.create(baseUrl+"/api/grade-estimates")).timeout(Duration.ofSeconds(70))
                    .header("Authorization","Bearer "+token).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(evidence))).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200) throw new IllegalStateException("AI estimation unavailable");
            var result=json.readTree(response.body());
            if (!result.path("officialGrade").isBoolean() || result.path("officialGrade").asBoolean()
                    || result.path("maxScore").decimalValue().compareTo(java.math.BigDecimal.valueOf(20))!=0
                    || !evidence.evaluationId().toString().equals(result.path("evaluationId").asText())
                    || !evidence.configurationId().toString().equals(result.path("configurationId").asText())
                    || !evidence.rubricId().toString().equals(result.path("rubricId").asText())
                    || evidence.rubricVersion()!=result.path("rubricVersion").asInt()
                    || !evidence.commitSha().equals(result.path("commitSha").asText()))
                throw new IllegalStateException("AI estimation does not match evaluation evidence");
            var score=result.path("estimatedScore");
            if (!java.util.Set.of("COMPLETE","PARTIAL").contains(result.path("status").asText())
                    || !score.isNull() && (!score.isNumber() || score.decimalValue().signum()<0
                    || score.decimalValue().compareTo(java.math.BigDecimal.valueOf(20))>0)
                    || "COMPLETE".equals(result.path("status").asText()) && !score.isNumber()
                    || "PARTIAL".equals(result.path("status").asText()) && !score.isNull())
                throw new IllegalStateException("Invalid AI score estimate");
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("AI estimation interrupted");
        } catch (java.io.IOException exception) { throw new IllegalStateException("AI estimation unavailable"); }
    }

    public com.fasterxml.jackson.databind.JsonNode propose(
            com.prgate.metrics.domain.model.valueobjects.PublishedRubric rubric, String language) {
        if (token.isBlank()) throw new IllegalStateException("AI backend credentials are not configured");
        var body = java.util.Map.of("rubricId", rubric.id().value().toString(),
                "rubricVersion", rubric.version().value(), "language", language,
                "criteria", rubric.criteria().stream().map(c -> java.util.Map.of(
                        "id", c.id().value(), "description", c.description(), "objective", c.objective())).toList());
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/technical-plans"))
                    .timeout(Duration.ofSeconds(125)).header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) throw new IllegalStateException("AI proposal unavailable");
            var plan = json.readTree(response.body());
            if (!rubric.id().value().toString().equals(plan.path("rubricId").asText())
                    || rubric.version().value() != plan.path("rubricVersion").asInt()
                    || !plan.path("checks").isArray()) throw new IllegalStateException("Invalid AI proposal");
            return plan;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI proposal interrupted");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("AI proposal unavailable");
        }
    }

    public com.fasterxml.jackson.databind.JsonNode analyzeDocument(String text) {
        if (token.isBlank()) throw new IllegalStateException("AI backend credentials are not configured");
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/rubric-documents/analysis"))
                    .timeout(Duration.ofSeconds(125)).header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(java.util.Map.of("text", text)))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("AI document analysis unavailable");
            return json.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI document analysis interrupted");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("AI document analysis unavailable");
        }
    }

    public com.fasterxml.jackson.databind.JsonNode practiceQuestions(java.util.List<String> findings) {
        if (token.isBlank()) throw new IllegalStateException("AI backend credentials are not configured");
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/practice-questions"))
                    .timeout(Duration.ofSeconds(125)).header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(java.util.Map.of("findings", findings)))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Practice questions unavailable");
            return json.readTree(response.body()).path("questions");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Practice generation interrupted");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Practice questions unavailable");
        }
    }
}
