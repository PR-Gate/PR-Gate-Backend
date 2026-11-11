package com.prgate.ai.application.port.in;

import com.prgate.ai.application.dto.*;
import com.prgate.ai.domain.model.valueobjects.AssessmentId;

public interface AssessmentUseCases {
    AssessmentDetails record(RecordAnalysisCommand command);
    AssessmentDetails get(AssessmentId id);
    AssessmentFeedbackResult retryFeedback(AssessmentId id);
}
