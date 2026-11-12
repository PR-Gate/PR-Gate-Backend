package com.prgate.users.application.github;
import java.time.Instant;
import java.util.UUID;
/** Short-lived secret material; intentionally redacted if accidentally logged. */
public record PendingGitHubAuthorization(String stateHash, UUID userId, String verifier,
                                         String browserHash, Instant expiresAt) {
    @Override public String toString() { return "PendingGitHubAuthorization[REDACTED]"; }
}
