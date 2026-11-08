package com.prgate.ai.infrastructure.inbound.rest;

import com.prgate.ai.application.dto.*;
import com.prgate.ai.domain.model.enums.*;
import com.prgate.ai.domain.model.valueobjects.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

/** Flat HTTP contracts, independent from aggregate and provider JSON shapes. */
public final class ApiResources {
    private ApiResources() {}

    public record CriterionRequest(@NotNull UUID id, @NotBlank @Size(max=4000) String description,
                                   @NotBlank @Size(max=4000) String objective) {
        RubricCriterion toDomain() { return new RubricCriterion(new CriterionId(id), description, objective); }
    }
    public record CreatePlanRequest(@NotBlank @Size(max=200) String rubricId, @Min(1) int rubricVersion,
                                    @NotBlank @Size(max=40) String language,
                                    @NotEmpty @Size(max=100) List<@NotNull @Valid CriterionRequest> criteria) {
        TranslateRubricCommand toCommand() {
            return new TranslateRubricCommand(new RubricReference(rubricId, rubricVersion),
                    new SourceLanguage(language), criteria.stream().map(CriterionRequest::toDomain).toList());
        }
    }
    public record CriterionResource(UUID id, String description, String objective) {}
    public record CheckResource(UUID criterionId, CheckMechanism mechanism, String target,
                                Map<String,String> parameters, String rationale) {}
    public record PlanResource(UUID id, String rubricId, int rubricVersion, String language,
            PlanStatus status, List<CriterionResource> criteria, List<CheckResource> checks, String catalogVersion) {
        static PlanResource from(TechnicalPlanResult p) {
            return new PlanResource(p.id().value(), p.rubric().id(), p.rubric().version(), p.language().value(),
                p.status(), p.criteria().stream().map(c -> new CriterionResource(c.id().value(), c.description(), c.objective())).toList(),
                p.checks().stream().map(c -> new CheckResource(c.criterionId().value(), c.mechanism(),
                        c.target(), c.parameters(), c.rationale())).toList(), p.catalogVersion().orElse(null));
        }
    }
    public record FindingRequest(@NotNull UUID id, @NotNull UUID criterionId,
            @NotBlank @Size(max=200) String ruleKey, @NotBlank @Size(max=2000) String filePath,
            @Min(1) int line, @NotBlank @Size(max=8000) String message) {
        TechnicalFinding toDomain() {
            return new TechnicalFinding(new FindingId(id), new CriterionId(criterionId), ruleKey, filePath, line, message);
        }
    }
    public record RecordAnalysisRequest(@NotNull UUID planId,
            @NotBlank @Size(max=200) String studentId, @NotBlank @Size(max=200) String submissionId,
            @NotBlank @Size(max=200) String revision, @NotBlank @Size(max=200) String analysisId,
            @NotNull @Size(max=2000) List<@NotNull @Valid FindingRequest> findings) {
        RecordAnalysisCommand toCommand() {
            return new RecordAnalysisCommand(new PlanId(planId), new SubmissionReference(studentId, submissionId, revision),
                    analysisId, findings.stream().map(FindingRequest::toDomain).toList());
        }
    }
    public record FindingResource(UUID id, UUID criterionId, String ruleKey, String filePath, int line, String message) {}
    public record FeedbackItemResource(UUID criterionId, Set<UUID> evidenceIds, String explanation, String suggestion) {
        static FeedbackItemResource from(FeedbackItem f) {
            return new FeedbackItemResource(f.criterionId().value(),
                    f.evidenceIds().stream().map(FindingId::value).collect(java.util.stream.Collectors.toSet()),
                    f.explanation(), f.suggestion());
        }
    }
    public record FeedbackResource(UUID assessmentId, String summary, List<FeedbackItemResource> feedback) {
        static FeedbackResource from(AssessmentFeedbackResult f) {
            return new FeedbackResource(f.assessmentId().value(), f.result().summary(),
                    f.result().feedback().stream().map(FeedbackItemResource::from).toList());
        }
    }
    public record AssessmentResource(UUID id, UUID planId, String studentId, String submissionId, String revision,
            AssessmentStatus status, String analysisId, List<FindingResource> findings, String summary,
            List<FeedbackItemResource> feedback, String failureReason) {
        static AssessmentResource from(AssessmentDetails a) {
            return new AssessmentResource(a.id().value(), a.planId().value(), a.submission().studentId(),
                a.submission().submissionId(), a.submission().revision(), a.status(), a.analysisId(),
                a.findings().stream().map(f -> new FindingResource(f.id().value(), f.criterionId().value(),
                        f.ruleKey(), f.filePath(), f.line(), f.message())).toList(),
                a.summary(), a.feedback().stream().map(FeedbackItemResource::from).toList(), a.failureReason());
        }
    }
}
