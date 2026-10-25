package com.prgate.projects.application.exception;

/** Users could not be consulted reliably; this is not equivalent to a missing user. */
public final class UserDirectoryException extends RuntimeException {

    public UserDirectoryException(String message) {
        super(message);
    }

    public UserDirectoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
