package com.prgate.projects.application.exception;

public final class EnrollmentConflictException extends RuntimeException {

    public EnrollmentConflictException(String message) {
        super(message);
    }

    public EnrollmentConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
