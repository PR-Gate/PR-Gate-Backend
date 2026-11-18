package com.prgate.metrics.application.dto;

import com.prgate.metrics.domain.model.valueobjects.*;
import com.prgate.metrics.domain.model.enums.ComparisonOperator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Persistence snapshot of an approved version; never a mutable reference to a rubric. */
public record StoredConfiguration(UUID id, UUID projectId, long repositoryId, String organization,
        String gitRef, PublishedRubric rubric, UUID planId, List<CheckDefinition> checks,
        UUID approvedBy, Instant approvedAt, String catalogVersion) {
    public StoredConfiguration { checks = List.copyOf(checks); }
    public ApprovedConfiguration basis() {
        var automatic = checks.stream().filter(c -> !c.type().equals("MANUAL"))
                .map(c -> new CheckId(c.id())).collect(java.util.stream.Collectors.toSet());
        return new ApprovedConfiguration(new ConfigurationId(id), rubric,
                new TechnicalPlanReference(planId, rubric.id(), rubric.version()),
                checks.stream().map(CheckDefinition::toDomain).toList(),
                new TeacherApproval(approvedBy, approvedAt), automatic.isEmpty() ? Optional.empty()
                : Optional.of(new CatalogValidation(planId, catalogVersion, automatic, approvedAt)));
    }
    public record CheckDefinition(UUID id, UUID criterionId, String type, String key,
                                  ComparisonOperator operator, BigDecimal threshold) {
        public EvaluationCheck toDomain() {
            CheckSpecification spec = switch (type) {
                case "METRIC" -> new MetricCheck(key, operator, threshold);
                case "RULE" -> new RuleCheck(key);
                case "MANUAL" -> new ManualCheck(key);
                default -> throw new IllegalArgumentException("Unsupported check type");
            };
            return new EvaluationCheck(new CheckId(id), new CriterionId(criterionId), spec);
        }
    }
}
