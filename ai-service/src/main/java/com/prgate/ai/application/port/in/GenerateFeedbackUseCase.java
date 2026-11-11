package com.prgate.ai.application.port.in;

import com.prgate.ai.application.dto.AssessmentFeedbackResult;
import com.prgate.ai.domain.model.valueobjects.AssessmentId;

public interface GenerateFeedbackUseCase {
    AssessmentFeedbackResult generate(AssessmentId id);
}
