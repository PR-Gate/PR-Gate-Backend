package com.prgate.ai.application.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public record GradeEstimateResult(UUID id,UUID evaluationId,UUID configurationId,String commitSha,
        UUID rubricId,int rubricVersion,String status,boolean officialGrade,BigDecimal maxScore,
        BigDecimal estimatedScore,BigDecimal estimatedSubtotal,BigDecimal coveredMaxPoints,
        String summary,List<CriterionEstimate> criteria,String provider,String model,Instant generatedAt) {
    public record CriterionEstimate(UUID criterionId,BigDecimal estimatedPoints,String justification,List<UUID> supportingCheckIds) { }
}
