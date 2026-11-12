package com.prgate.users.application.github;
import com.prgate.users.domain.model.GitHubAccountLink;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import static com.prgate.users.application.github.GitHubLinkException.Reason.*;
public final class GitHubLinkService {
    private final GitHubLinkStore store;
    private final GitHubIdentityProvider provider;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    public GitHubLinkService(GitHubLinkStore store, GitHubIdentityProvider provider, Clock clock) {
        this.store = store; this.provider = provider; this.clock = clock;
    }
    public Authorization begin(UUID userId) {
        String state = secret(), verifier = secret(), browser = secret();
        var expires = clock.instant().plusSeconds(600);
        var uri = provider.authorizationUri(state, digest(verifier));
        store.begin(new PendingGitHubAuthorization(digest(state), userId, verifier, digest(browser), expires), clock.instant());
        return new Authorization(uri, browser, expires);
    }
    public GitHubAccountLink complete(String state, String browserSecret, String code, String error) {
        if (state == null || !state.matches("[A-Za-z0-9_-]{43}")
                || browserSecret == null || !browserSecret.matches("[A-Za-z0-9_-]{43}"))
            throw new GitHubLinkException(INVALID_AUTHORIZATION);
        var pending = store.consume(digest(state), digest(browserSecret), clock.instant());
        try {
            if (error != null) throw new GitHubLinkException(AUTHORIZATION_CANCELLED);
            if (code == null || code.isBlank() || code.length() > 512)
                throw new GitHubLinkException(INVALID_AUTHORIZATION);
            var identity = provider.identify(code, pending.verifier());
            return store.finish(pending, identity, clock.instant());
        } finally { store.discard(pending.stateHash()); }
    }
    public GitHubAccountLink get(UUID userId) {
        return store.find(userId).orElseThrow(() -> new GitHubLinkException(NOT_LINKED));
    }
    public void unlink(UUID userId) { store.unlink(userId); }
    private String secret() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String digest(String value) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    public record Authorization(URI authorizationUrl, String browserSecret, Instant expiresAt) {
        @Override public String toString() { return "Authorization[REDACTED]"; }
    }
}
