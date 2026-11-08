package com.prgate.ai.application.exception;

public final class InvalidTechnicalProposalException extends RuntimeException {
    public InvalidTechnicalProposalException() {
        super("Generated technical proposal does not satisfy the rubric");
    }
}
