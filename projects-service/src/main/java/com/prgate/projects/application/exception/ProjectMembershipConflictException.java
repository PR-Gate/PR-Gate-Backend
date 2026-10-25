package com.prgate.projects.application.exception;

public final class ProjectMembershipConflictException extends RuntimeException {

    public ProjectMembershipConflictException(String message) {
        super(message);
    }

    public ProjectMembershipConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
