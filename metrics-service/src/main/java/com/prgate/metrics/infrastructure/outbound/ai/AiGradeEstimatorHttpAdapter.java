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
}
