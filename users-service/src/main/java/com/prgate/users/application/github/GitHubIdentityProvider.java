package com.prgate.users.application.github;
import java.net.URI;
public interface GitHubIdentityProvider {
    URI authorizationUri(String state, String challenge);
    Identity identify(String code, String verifier);
    record Identity(long id, String login, @com.fasterxml.jackson.annotation.JsonIgnore String accessToken,
                    java.time.Instant expiresAt) {
        public Identity(long id, String login) { this(id, login, null, null); }
        public Identity {
            if (id <= 0 || login == null || login.isBlank() || login.length() > 100)
                throw new GitHubLinkException(GitHubLinkException.Reason.PROVIDER_UNAVAILABLE);
        }
        @Override public String toString() { return "GitHubIdentity[REDACTED]"; }
    }
}
