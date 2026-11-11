package com.prgate.ai.application.exception;

import com.prgate.ai.domain.model.valueobjects.AssessmentId;

public final class AssessmentNotFoundException extends RuntimeException {
    public AssessmentNotFoundException(AssessmentId id) {
        super("Assessment not found: " + id.value());
    }
}
