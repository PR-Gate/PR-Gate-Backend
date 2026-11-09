package com.prgate.projects.application.exception;

public class GitHubDirectoryException extends RuntimeException {
    public GitHubDirectoryException(String message) { super(message); }
    public GitHubDirectoryException(String message, Throwable cause) { super(message, cause); }
}
