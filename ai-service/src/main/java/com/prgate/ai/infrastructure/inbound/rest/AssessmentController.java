package com.prgate.ai.infrastructure.inbound.rest;

import com.prgate.ai.application.port.in.*;
import com.prgate.ai.domain.model.valueobjects.AssessmentId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;
import static com.prgate.ai.infrastructure.inbound.rest.ApiResources.*;

@RestController
@RequestMapping("/api/assessments")
public final class AssessmentController {
    private final AssessmentUseCases assessments;
    private final GenerateFeedbackUseCase feedback;
    public AssessmentController(AssessmentUseCases assessments, GenerateFeedbackUseCase feedback) {
        this.assessments = assessments; this.feedback = feedback;
    }
    /** Trusted backend ingestion of already computed findings, not a scanner endpoint. */
    @PostMapping
    public ResponseEntity<AssessmentResource> record(@Valid @RequestBody RecordAnalysisRequest request) {
        var result = AssessmentResource.from(assessments.record(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/assessments/" + result.id())).body(result);
    }
    @GetMapping("/{id}")
    public AssessmentResource get(@PathVariable UUID id) { return AssessmentResource.from(assessments.get(new AssessmentId(id))); }
    @PostMapping("/{id}/feedback")
    public FeedbackResource feedback(@PathVariable UUID id) { return FeedbackResource.from(feedback.generate(new AssessmentId(id))); }
    @PostMapping("/{id}/feedback/retry")
    public FeedbackResource retry(@PathVariable UUID id) { return FeedbackResource.from(assessments.retryFeedback(new AssessmentId(id))); }
}
