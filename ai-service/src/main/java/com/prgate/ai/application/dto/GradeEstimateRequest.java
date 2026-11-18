package com.prgate.ai.application.dto;
import java.math.BigDecimal;
import java.util.*;

/** Evidence provided by the trusted Metrics backend, never a direct student score request. */
public record GradeEstimateRequest(UUID evaluationId, UUID configurationId, String commitSha,
        UUID rubricId, int rubricVersion, List<CriterionEvidence> criteria) {
    public GradeEstimateRequest {
        Objects.requireNonNull(evaluationId); Objects.requireNonNull(configurationId); Objects.requireNonNull(rubricId);
        if (commitSha==null || !commitSha.matches("[a-f0-9]{40}|[a-f0-9]{64}") || rubricVersion<1)
            throw new IllegalArgumentException("Invalid estimation revision");
        criteria=List.copyOf(criteria);
        if (criteria.isEmpty() || criteria.size()>100 || criteria.stream().mapToInt(c->c.checks().size()).sum()>500
                || criteria.stream().map(CriterionEvidence::criterionId).distinct().count()!=criteria.size()
                || criteria.stream().map(CriterionEvidence::maxPoints).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(BigDecimal.valueOf(20))!=0)
            throw new IllegalArgumentException("Rubric must contain unique criteria totaling 20 points");
    }
    public record CriterionEvidence(UUID criterionId,String description,BigDecimal maxPoints,
                                     boolean evidenceComplete,List<CheckEvidence> checks) {
        public CriterionEvidence {
            Objects.requireNonNull(criterionId);
            if (description==null || description.isBlank() || description.length()>4000 || maxPoints==null
                    || maxPoints.signum()<=0 || maxPoints.compareTo(BigDecimal.valueOf(20))>0)
                throw new IllegalArgumentException("Invalid rubric criterion");
            checks=List.copyOf(checks);
            if (checks.size()>500 || checks.stream().map(CheckEvidence::id).distinct().count()!=checks.size()
                    || evidenceComplete && (checks.isEmpty() || checks.stream().anyMatch(c->c.status().equals("NOT_EVALUABLE"))))
                throw new IllegalArgumentException("Invalid criterion evidence completeness");
        }
    }
    public record CheckEvidence(UUID id,String type,String status,String target,BigDecimal observedValue,
                                 String operator,BigDecimal threshold,String reason) {
        public CheckEvidence {
            Objects.requireNonNull(id);
            if (!Set.of("METRIC","RULE","MANUAL").contains(type) || !Set.of("PASSED","FAILED","NOT_EVALUABLE").contains(status)
                    || target==null || target.isBlank() || target.length()>2000 || reason==null || reason.length()>4000)
                throw new IllegalArgumentException("Invalid technical check evidence");
        }
    }
}
