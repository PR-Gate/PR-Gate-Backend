package com.prgate.ai.application.dto;

import java.util.List;
import com.prgate.ai.domain.model.aggregates.Assessment;
import com.prgate.ai.domain.model.enums.AssessmentStatus;
import com.prgate.ai.domain.model.valueobjects.*;

public record AssessmentDetails(AssessmentId id, PlanId planId, SubmissionReference submission,
        AssessmentStatus status, String analysisId, List<TechnicalFinding> findings,
        String summary, List<FeedbackItem> feedback, String failureReason) {
    public AssessmentDetails {
        findings = List.copyOf(findings);
        feedback = List.copyOf(feedback);
    }
    public static AssessmentDetails from(Assessment a) {
        return new AssessmentDetails(a.id(), a.planId(), a.submission(), a.status(),
                a.analysisId().orElse(null), a.findings(), a.summary().orElse(null),
                a.feedback(), a.failureReason().orElse(null));
    }
}
