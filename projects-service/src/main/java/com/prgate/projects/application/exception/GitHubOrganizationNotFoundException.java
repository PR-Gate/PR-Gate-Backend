package com.prgate.projects.application.exception;

public class GitHubOrganizationNotFoundException extends RuntimeException {
    public GitHubOrganizationNotFoundException(String message) { super(message); }
    public GitHubOrganizationNotFoundException(String message, Throwable cause) { super(message, cause); }
}
