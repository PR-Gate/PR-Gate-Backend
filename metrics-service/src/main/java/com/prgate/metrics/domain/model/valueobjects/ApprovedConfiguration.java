package com.prgate.metrics.domain.model.valueobjects;
import java.util.*;
/** Immutable evaluation basis; validates even when reconstructed outside the aggregate. */
public record ApprovedConfiguration(ConfigurationId id, PublishedRubric rubric,
        TechnicalPlanReference plan, List<EvaluationCheck> checks,
        TeacherApproval approval, Optional<CatalogValidation> catalogValidation) {
    public ApprovedConfiguration {
        Checks.required(id); Checks.required(rubric); Checks.required(plan); Checks.required(approval);
        checks = List.copyOf(Checks.required(checks)); Checks.required(catalogValidation);
        Checks.require(plan.rubricId().equals(rubric.id()) && plan.rubricVersion().equals(rubric.version()),
                "technical plan must reference the same rubric version");
        Checks.require(checks.stream().map(EvaluationCheck::id).distinct().count() == checks.size(),
                "check identifiers must be unique");
        var criteriaIds = new HashSet<CriterionId>();
        rubric.criteria().forEach(c -> criteriaIds.add(c.id()));
        var covered = new HashSet<CriterionId>();
        checks.forEach(c -> covered.add(c.criterionId()));
        Checks.require(covered.equals(criteriaIds), "checks must cover exactly the published criteria");
        var automaticIds = new HashSet<CheckId>();
        checks.stream().filter(c -> !(c.specification() instanceof ManualCheck)).forEach(c -> automaticIds.add(c.id()));
        if (!automaticIds.isEmpty()) {
            var validation = catalogValidation.orElseThrow(() ->
                    new com.prgate.metrics.domain.exception.DomainRuleViolation("automatic checks need catalog validation"));
            Checks.require(validation.planId().equals(plan.planId()), "catalog validation belongs to another plan");
            Checks.require(validation.checkIds().equals(automaticIds), "catalog validation must cover exact automatic checks");
            Checks.require(!validation.validatedAt().isAfter(approval.approvedAt()), "catalog validation must precede approval");
        }
    }
}
