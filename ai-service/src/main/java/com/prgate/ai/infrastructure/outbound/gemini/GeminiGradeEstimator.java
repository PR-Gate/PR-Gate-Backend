package com.prgate.ai.infrastructure.outbound.gemini;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.ai.application.dto.GradeEstimateRequest;
import com.prgate.ai.application.port.out.*;
import com.prgate.ai.infrastructure.outbound.GradeEstimateFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="ai",name="provider",havingValue="gemini")
public class GeminiGradeEstimator implements GradeEstimationGenerator {
    private final GeminiClient client;
    private final ObjectMapper json;
    private final String model;
    public GeminiGradeEstimator(GeminiClient client,ObjectMapper json,@Value("${gemini.model}") String model) {
        this.client=client; this.json=json; this.model=model;
    }
    public Generation estimate(GradeEstimateRequest request) {
        var response=client.generate(GradeEstimateFormat.INSTRUCTIONS,request,GradeEstimateFormat.schema(),12000);
        try {
            var result=json.treeToValue(response,GradeEstimateFormat.Generated.class);
            return new Generation(result.summary(),result.criteria(),"gemini",model);
        } catch (java.io.IOException exception) { throw new FeedbackGenerationException("Invalid Gemini grade estimate"); }
    }
}
