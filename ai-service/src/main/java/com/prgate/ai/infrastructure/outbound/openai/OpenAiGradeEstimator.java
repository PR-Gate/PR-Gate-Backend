package com.prgate.ai.infrastructure.outbound.openai;
import com.fasterxml.jackson.databind.*;
import com.prgate.ai.application.dto.GradeEstimateRequest;
import com.prgate.ai.application.port.out.*;
import com.prgate.ai.infrastructure.outbound.GradeEstimateFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix="ai",name="provider",havingValue="openai",matchIfMissing=true)
public class OpenAiGradeEstimator implements GradeEstimationGenerator {
    private final RestClient client;
    private final ObjectMapper json;
    private final String model;
    public OpenAiGradeEstimator(RestClient.Builder builder,ObjectMapper json,@Value("${openai.api-key}") String key,
            @Value("${openai.model}") String model,@Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.client=builder.clone().baseUrl(baseUrl).requestFactory(factory).defaultHeader("Authorization","Bearer "+key).build();
        this.json=json; this.model=model;
    }
    public Generation estimate(GradeEstimateRequest request) {
        try {
            var body=Map.of("model",model,"store",false,"max_output_tokens",12000,
                    "instructions",GradeEstimateFormat.INSTRUCTIONS,"input",json.writeValueAsString(request),
                    "text",Map.of("format",Map.of("type","json_schema","name","grade_estimate","strict",true,
                            "schema",GradeEstimateFormat.schema())));
            JsonNode response=client.post().uri("/responses").contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class);
            if (response==null || !"completed".equals(response.path("status").asText())) throw new IllegalArgumentException();
            var content=new StringBuilder();
            for (var output:response.path("output")) if (output.path("type").asText().equals("message")) {
                for (var part:output.path("content")) {
                    if (part.path("type").asText().equals("refusal")) throw new IllegalArgumentException();
                    if (part.path("type").asText().equals("output_text")) content.append(part.path("text").asText());
                }
            }
            var result=json.readValue(content.toString(),GradeEstimateFormat.Generated.class);
            return new Generation(result.summary(),result.criteria(),"openai",model);
        } catch (Exception exception) { throw new FeedbackGenerationException("OpenAI grade estimation did not return a valid result"); }
    }
}
