package com.prgate.metrics.application.dto;
import java.math.BigDecimal;
import java.util.*;

public record GradeEstimationEvidence(UUID evaluationId,UUID configurationId,String commitSha,
        UUID rubricId,int rubricVersion,List<CriterionEvidence> criteria) {
    public record CriterionEvidence(UUID criterionId,String description,BigDecimal maxPoints,
                                     boolean evidenceComplete,List<CheckEvidence> checks) { }
    public record CheckEvidence(UUID id,String type,String status,String target,BigDecimal observedValue,
                                 String operator,BigDecimal threshold,String reason) { }
}
