package com.prgate.metrics.infrastructure.inbound.rest;

import com.prgate.metrics.application.service.RubricEvaluationService;
import com.prgate.metrics.infrastructure.inbound.rest.EvaluationController.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/courses/{courseId}/rubrics")
public class CourseRubricController {
    private final RubricEvaluationService service;
    private final com.prgate.metrics.infrastructure.outbound.ai.AiGradeEstimatorHttpAdapter ai;
    public CourseRubricController(RubricEvaluationService service,
            com.prgate.metrics.infrastructure.outbound.ai.AiGradeEstimatorHttpAdapter ai) {
        this.service = service;
        this.ai = ai;
    }

    @PostMapping("/{rubricId}/versions/{version}/technical-proposal")
    public com.fasterxml.jackson.databind.JsonNode propose(@RequestAttribute("verifiedActorId") UUID actor,
            @PathVariable UUID courseId, @PathVariable UUID rubricId, @PathVariable int version,
            @Valid @RequestBody ProposalRequest request) {
        return ai.propose(service.rubricForProposal(actor, courseId, rubricId, version), request.language());
    }

    public record ProposalRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=40) String language) { }

    @PostMapping("/document-analysis")
    public com.fasterxml.jackson.databind.JsonNode analyzeDocument(@RequestAttribute("verifiedActorId") UUID actor,
            @PathVariable UUID courseId, @Valid @RequestBody DocumentRequest request) {
        service.requireCourseTeacher(actor, courseId);
        return ai.analyzeDocument(request.text());
    }

    public record DocumentRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=100000) String text) { }

    @GetMapping
    public List<RubricResource> list(@RequestAttribute("verifiedActorId") UUID actor,
            @PathVariable UUID courseId, @RequestParam(defaultValue="0") int page) {
        return service.courseRubrics(actor, courseId, page, 100).stream().map(RubricResource::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RubricResource create(@RequestAttribute("verifiedActorId") UUID actor,
            @PathVariable UUID courseId, @Valid @RequestBody PublishRequest request) {
        return version(actor, courseId, null, request);
    }

    @PostMapping("/{rubricId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public RubricResource version(@RequestAttribute("verifiedActorId") UUID actor,
            @PathVariable UUID courseId, @PathVariable UUID rubricId, @Valid @RequestBody PublishRequest request) {
        return RubricResource.from(service.publishCourse(actor, courseId, rubricId, request.title(),
                request.criteria().stream().map(CriterionRequest::domain).toList()));
    }

    @PostMapping("/{rubricId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@RequestAttribute("verifiedActorId") UUID actor,
            @PathVariable UUID courseId, @PathVariable UUID rubricId) {
        service.archiveCourseRubric(actor, courseId, rubricId);
    }
}
