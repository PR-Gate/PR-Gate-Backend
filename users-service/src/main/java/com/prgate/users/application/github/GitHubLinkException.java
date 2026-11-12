package com.prgate.users.application.github;
/** Fixed, safe public errors; never propagate provider messages or response bodies. */
public final class GitHubLinkException extends RuntimeException {
    public enum Reason {
        AUTHENTICATION_REQUIRED(401), USER_UNAVAILABLE(403), INVALID_AUTHORIZATION(400),
        AUTHORIZATION_CANCELLED(400), ALREADY_LINKED(409), IDENTITY_IN_USE(409),
        NOT_LINKED(404), PROVIDER_UNAVAILABLE(502), REAUTHORIZATION_REQUIRED(409),
        TOKEN_STORAGE_UNAVAILABLE(503), REPOSITORY_ACCESS_DENIED(403), RATE_LIMITED(429);
        private final int status;
        Reason(int status) { this.status = status; }
        public int status() { return status; }
    }
    private final Reason reason;
    public GitHubLinkException(Reason reason) { super(reason.name()); this.reason = reason; }
    public Reason reason() { return reason; }
}
