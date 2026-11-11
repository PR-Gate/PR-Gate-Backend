package com.prgate.ai.application.dto;

import com.prgate.ai.domain.model.valueobjects.AssessmentId;
import static com.prgate.ai.domain.model.valueobjects.Checks.present;

public record AssessmentFeedbackResult(AssessmentId assessmentId, FeedbackResult result) {
    public AssessmentFeedbackResult {
        present(assessmentId);
        present(result);
    }
}
