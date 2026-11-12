package com.prgate.users.domain.model;
import java.time.Instant;
import java.util.UUID;
/** Public identity DTO. Credentials are encrypted separately and never included in this DTO. */
public record GitHubAccountLink(UUID userId, long githubUserId, String login, Instant linkedAt) {
    public GitHubAccountLink {
        if (userId == null || githubUserId <= 0 || login == null || login.isBlank()
                || login.length() > 100 || linkedAt == null) throw new IllegalArgumentException("Invalid GitHub identity");
    }
}
