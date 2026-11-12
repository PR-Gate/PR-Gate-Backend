package com.prgate.users.application.github;
import com.prgate.users.domain.model.GitHubAccountLink;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
public interface GitHubLinkStore {
    void begin(PendingGitHubAuthorization pending, Instant now);
    PendingGitHubAuthorization consume(String stateHash, String browserHash, Instant now);
    GitHubAccountLink finish(PendingGitHubAuthorization pending, GitHubIdentityProvider.Identity identity, Instant now);
    void discard(String stateHash);
    Optional<GitHubAccountLink> find(UUID userId);
    void unlink(UUID userId);
}
