package com.prgate.metrics.infrastructure.inbound.rest;

import com.prgate.metrics.application.dto.StoredConfiguration;
import com.prgate.metrics.application.port.out.EvaluationStore;
import com.prgate.metrics.application.service.RubricEvaluationService;
import com.prgate.metrics.domain.model.entities.RubricCriterion;
import com.prgate.metrics.domain.model.enums.ComparisonOperator;
import com.prgate.metrics.domain.model.valueobjects.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

/** Internal backend API. Actor IDs are accepted only behind service authentication. */
@RestController
@RequestMapping("/internal/v1/projects/{projectId}")
public class EvaluationController {
    private final RubricEvaluationService service;
    private final com.prgate.metrics.application.service.GradeEstimationService estimates;
    public EvaluationController(RubricEvaluationService service, com.prgate.metrics.application.service.GradeEstimationService estimates) {
        this.service = service; this.estimates=estimates;
    }
    @PostMapping(value="/evaluations/{evaluationId}/grade-estimate",produces="application/json")
    public String estimate(@RequestAttribute("verifiedActorId") UUID actor,@PathVariable UUID projectId,@PathVariable UUID evaluationId) {
        return estimates.generate(actor,projectId,evaluationId);
    }
    @GetMapping(value="/evaluations/{evaluationId}/grade-estimate",produces="application/json")
    public String estimated(@RequestAttribute("verifiedActorId") UUID actor,@PathVariable UUID projectId,@PathVariable UUID evaluationId) {
        return estimates.get(actor,projectId,evaluationId);
    }

    @PostMapping("/rubrics")
    @ResponseStatus(HttpStatus.CREATED)
    public RubricResource publish(@RequestAttribute("verifiedActorId") UUID actor, @PathVariable UUID projectId,
                                  @Valid @RequestBody PublishRequest request) {
        return RubricResource.from(service.publish(actor, projectId, null, request.title(), request.criteria().stream().map(CriterionRequest::domain).toList()));
    }
    @GetMapping("/rubrics")
    public List<RubricResource> rubrics(@RequestAttribute("verifiedActorId") UUID actor,@PathVariable UUID projectId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="100") int size) {
        return service.rubrics(actor,projectId,page,size).stream().map(RubricResource::from).toList();
    }
    @GetMapping("/repositories")
    public List<EvaluationStore.RepositoryChoice> repositories(@RequestAttribute("verifiedActorId") UUID actor,@PathVariable UUID projectId) {
        return service.repositories(actor,projectId);
    }
    @GetMapping("/analyses")
    public List<com.prgate.metrics.application.dto.AnalysisCompletedEvent> analyses(@RequestAttribute("verifiedActorId") UUID actor,
            @PathVariable UUID projectId,@RequestParam long repositoryId,@RequestParam String gitRef,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.analyses(actor,projectId,repositoryId,gitRef,page,size);
    }
    @GetMapping("/configurations")
    public List<ConfigurationResource> configurations(@RequestAttribute("verifiedActorId") UUID actor,@PathVariable UUID projectId,
                                                      @RequestParam long repositoryId) {
        return service.configurations(actor,projectId,repositoryId).stream().map(ConfigurationResource::from).toList();
    }
    @PostMapping("/rubrics/{rubricId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public RubricResource version(@RequestAttribute("verifiedActorId") UUID actor, @PathVariable UUID projectId,
            @PathVariable UUID rubricId, @Valid @RequestBody PublishRequest request) {
        return RubricResource.from(service.publish(actor, projectId, rubricId, request.title(), request.criteria().stream().map(CriterionRequest::domain).toList()));
    }
    @GetMapping("/rubrics/{rubricId}/versions/{version}")
    public RubricResource rubric(@RequestAttribute("verifiedActorId") UUID actor, @PathVariable UUID projectId,
                                  @PathVariable UUID rubricId, @PathVariable int version) {
        return RubricResource.from(service.rubric(actor, projectId, rubricId, version));
    }
    @PostMapping("/configurations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigurationResource approve(@RequestAttribute("verifiedActorId") UUID actor, @PathVariable UUID projectId,
                                       @Valid @RequestBody ConfigurationRequest request) {
        return ConfigurationResource.from(service.approve(actor, projectId, request.rubricId(), request.rubricVersion(), request.repositoryId(),
                request.gitRef(), request.planId(), request.checks().stream().map(CheckRequest::definition).toList()));
    }
    @GetMapping("/configurations/{configurationId}")
    public ConfigurationResource configuration(@RequestAttribute("verifiedActorId") UUID actor, @PathVariable UUID projectId,
                                              @PathVariable UUID configurationId) {
        return ConfigurationResource.from(service.configuration(actor, projectId, configurationId));
    }
    @GetMapping("/configurations/{configurationId}/quality-gate")
    public Map<String, Object> gate(@RequestAttribute("verifiedActorId") UUID actor, @PathVariable UUID projectId,
                                    @PathVariable UUID configurationId) {
        return service.gateStatus(actor, projectId, configurationId);
    }
    @PostMapping("/configurations/{configurationId}/quality-gate/retry")
    public Map<String,Object> retryGate(@RequestAttribute("verifiedActorId") UUID actor,@PathVariable UUID projectId,
                                       @PathVariable UUID configurationId) {
        return service.retryGate(actor,projectId,configurationId);
    }
    @PostMapping("/evaluations")
    public TechnicalEvaluationResource evaluate(@RequestAttribute("verifiedActorId") UUID actor,
            @PathVariable UUID projectId, @Valid @RequestBody EvaluationRequest request) {
        return TechnicalEvaluationResource.from(service.evaluate(actor, projectId, request.configurationId(), request.eventId()));
    }
    @GetMapping("/evaluations")
    public List<TechnicalEvaluationResource> list(@RequestAttribute("verifiedActorId") UUID actor,
            @PathVariable UUID projectId, @RequestParam long repositoryId,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return service.list(actor, projectId, repositoryId, page, size).stream().map(TechnicalEvaluationResource::from).toList();
    }
    @GetMapping("/evaluations/{evaluationId}")
    public TechnicalEvaluationResource get(@RequestAttribute("verifiedActorId") UUID actor,
            @PathVariable UUID projectId, @PathVariable UUID evaluationId) {
        return TechnicalEvaluationResource.from(service.get(actor, projectId, evaluationId));
    }
    @GetMapping("/evaluations/{evaluationId}/analysis")
    public RubricEvaluationService.AnalysisView analysis(@RequestAttribute("verifiedActorId") UUID actor,
            @PathVariable UUID projectId, @PathVariable UUID evaluationId) {
        return service.analysis(actor, projectId, evaluationId);
    }
    @PostMapping("/evaluations/{evaluationId}/manual-reviews")
    public TechnicalEvaluationResource review(@RequestAttribute("verifiedActorId") UUID actor,
            @PathVariable UUID projectId, @PathVariable UUID evaluationId, @Valid @RequestBody ReviewRequest request) {
        return TechnicalEvaluationResource.from(service.review(actor, projectId, evaluationId, request.checkId(), request.passed(), request.justification()));
    }
    @PostMapping("/evaluations/{evaluationId}/analysis/retry")
    public RubricEvaluationService.AnalysisView retryEvidence(@RequestAttribute("verifiedActorId") UUID actor,
            @PathVariable UUID projectId,@PathVariable UUID evaluationId) {
        return service.retryEvidence(actor,projectId,evaluationId);
    }

    public record PublishRequest(@NotBlank @Size(max=200) String title,
                                 @NotEmpty @Size(max=100) List<@NotNull @Valid CriterionRequest> criteria) { }
    public record CriterionRequest(UUID id, @NotBlank @Size(max=1000) String description,
                                   @NotBlank @Size(max=2000) String objective,
                                   @NotNull @DecimalMin("0.01") @DecimalMax("20") @Digits(integer=2,fraction=2) BigDecimal maxPoints) {
        RubricCriterion domain() {
            return new RubricCriterion(new CriterionId(id == null ? UUID.randomUUID() : id), description, objective,
                    new Weight(maxPoints.multiply(BigDecimal.valueOf(5))));
        }
    }
    public record ConfigurationRequest(@NotNull UUID rubricId, @Min(1) int rubricVersion,
            @Positive long repositoryId, @NotBlank @Size(max=1024) String gitRef, UUID planId,
            @NotEmpty @Size(max=500) List<@NotNull @Valid CheckRequest> checks) { }
    public record CheckRequest(UUID id, @NotNull UUID criterionId,
            @NotNull @Pattern(regexp="METRIC|RULE|MANUAL") String type,
            @NotBlank @Size(max=1000) String key, ComparisonOperator operator, BigDecimal threshold) {
        StoredConfiguration.CheckDefinition definition() {
            return new StoredConfiguration.CheckDefinition(id == null ? UUID.randomUUID() : id,
                    criterionId, type, key, operator, threshold);
        }
    }
    public record EvaluationRequest(@NotNull UUID configurationId, @NotNull UUID eventId) { }
    public record ReviewRequest(@NotNull UUID checkId, @NotNull Boolean passed,
                               @NotBlank @Size(max=2000) String justification) { }
    public record RubricResource(UUID id, int version, String title, BigDecimal maxPoints,
                                 List<CriterionRequest> criteria) {
        static RubricResource from(PublishedRubric rubric) {
            return new RubricResource(rubric.id().value(), rubric.version().value(), rubric.title(), BigDecimal.valueOf(20),
                    rubric.criteria().stream().map(c -> new CriterionRequest(c.id().value(), c.description(), c.objective(),
                            c.weight().value().divide(BigDecimal.valueOf(5)))).toList());
        }
    }
    public record ConfigurationResource(UUID id,UUID projectId,long repositoryId,String organization,String gitRef,
            RubricResource rubric,UUID planId,List<StoredConfiguration.CheckDefinition> checks,UUID approvedBy,java.time.Instant approvedAt) {
        static ConfigurationResource from(StoredConfiguration value) {
            return new ConfigurationResource(value.id(),value.projectId(),value.repositoryId(),value.organization(),value.gitRef(),
                    RubricResource.from(value.rubric()),value.planId(),value.checks(),value.approvedBy(),value.approvedAt());
        }
    }
    public record CriterionComplianceResource(UUID criterionId,BigDecimal maxPoints,String status,List<CheckResult> checks) { }
    public record TechnicalComplianceResource(BigDecimal evaluatedMaxPoints,BigDecimal provisionalCompliancePercent,
            BigDecimal completeCompliancePercent,List<CriterionComplianceResource> criteria) { }
    public record TechnicalEvaluationResource(UUID id,UUID projectId,UUID configurationId,UUID eventId,long repositoryId,
            String gitRef,String commitSha,String status,TechnicalComplianceResource technicalCompliance,
            List<ManualDecision> decisions,String evidenceState) {
        static TechnicalEvaluationResource from(EvaluationStore.StoredEvaluation value) {
            var summary=value.summary();
            var compliance=new TechnicalComplianceResource(summary.coverage().value().divide(BigDecimal.valueOf(5)),
                    summary.provisionalPercentage().map(CompliancePercentage::value).orElse(null),
                    summary.finalPercentage().map(CompliancePercentage::value).orElse(null),summary.criteria().stream()
                    .map(c->new CriterionComplianceResource(c.criterionId().value(),c.weight().value().divide(BigDecimal.valueOf(5)),
                            c.status().name(),c.checks())).toList());
            return new TechnicalEvaluationResource(value.id(),value.projectId(),value.configurationId(),value.eventId(),
                    value.repositoryId(),value.gitRef(),value.commitSha(),value.status(),compliance,value.decisions(),value.evidenceState());
        }
    }
}
