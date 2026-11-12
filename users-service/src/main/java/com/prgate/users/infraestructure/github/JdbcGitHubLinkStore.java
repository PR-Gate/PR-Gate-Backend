package com.prgate.users.infraestructure.github;
import com.prgate.users.application.github.*;
import com.prgate.users.domain.model.GitHubAccountLink;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static com.prgate.users.application.github.GitHubLinkException.Reason.*;
public class JdbcGitHubLinkStore implements GitHubLinkStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final GitHubTokenCipher cipher;
    public JdbcGitHubLinkStore(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this(jdbc, transactions, new GitHubTokenCipher(""));
    }
    public JdbcGitHubLinkStore(JdbcTemplate jdbc, TransactionTemplate transactions, GitHubTokenCipher cipher) {
        this.jdbc = jdbc; this.transactions = transactions;
        this.cipher = cipher;
    }
    private void lockActiveUser(UUID userId) {
        var users = jdbc.queryForList("SELECT status FROM users.users WHERE id=? FOR UPDATE", String.class, userId);
        if (users.size() != 1 || !"ACTIVE".equals(users.get(0))) throw new GitHubLinkException(USER_UNAVAILABLE);
    }
    public void begin(PendingGitHubAuthorization p, Instant now) {
        transactions.executeWithoutResult(tx -> {
            lockActiveUser(p.userId());
            // An existing link may renew authorization, but cannot change GitHub identity.
            jdbc.update("DELETE FROM users.github_authorizations WHERE user_id=?", p.userId());
            jdbc.update("INSERT INTO users.github_authorizations (state_hash,user_id,verifier,browser_hash,expires_at,consumed) VALUES (?,?,?,?,?,FALSE)",
                    p.stateHash(), p.userId(), p.verifier(), p.browserHash(), Timestamp.from(p.expiresAt()));
        });
    }
    public PendingGitHubAuthorization consume(String stateHash, String browserHash, Instant now) {
        return transactions.execute(tx -> {
            var found = jdbc.query("SELECT * FROM users.github_authorizations WHERE state_hash=? AND browser_hash=? AND expires_at>? AND consumed=FALSE",
                    (rs, row) -> new PendingGitHubAuthorization(rs.getString("state_hash"),
                            rs.getObject("user_id", UUID.class), rs.getString("verifier"),
                            rs.getString("browser_hash"), rs.getTimestamp("expires_at").toInstant()),
                    stateHash, browserHash, Timestamp.from(now));
            if (found.size() != 1) throw new GitHubLinkException(INVALID_AUTHORIZATION);
            int changed = jdbc.update("UPDATE users.github_authorizations SET consumed=TRUE WHERE state_hash=? AND consumed=FALSE AND expires_at>?",
                    stateHash, Timestamp.from(now));
            if (changed != 1) throw new GitHubLinkException(INVALID_AUTHORIZATION);
            return found.get(0);
        });
    }
    public GitHubAccountLink finish(PendingGitHubAuthorization pending, GitHubIdentityProvider.Identity identity, Instant now) {
        try {
            return transactions.execute(tx -> {
                lockActiveUser(pending.userId());
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users.github_authorizations WHERE state_hash=? AND user_id=? AND consumed=TRUE AND expires_at>?",
                        Integer.class, pending.stateHash(), pending.userId(), Timestamp.from(now));
                if (count == null || count != 1) throw new GitHubLinkException(INVALID_AUTHORIZATION);
                var previous = find(pending.userId());
                if (previous.isPresent() && previous.get().githubUserId() != identity.id())
                    throw new GitHubLinkException(ALREADY_LINKED);
                String encrypted = identity.accessToken() == null ? null : cipher.encrypt(identity.accessToken(), pending.userId() + ":" + identity.id());
                if (previous.isEmpty()) {
                    jdbc.update("INSERT INTO users.github_account_links (user_id,github_user_id,github_login,linked_at,access_token_ciphertext,access_token_expires_at) VALUES (?,?,?,?,?,?)",
                        pending.userId(), identity.id(), identity.login(), Timestamp.from(now), encrypted,
                        identity.expiresAt() == null ? null : Timestamp.from(identity.expiresAt()));
                } else {
                    if (encrypted == null) throw new GitHubLinkException(REAUTHORIZATION_REQUIRED);
                    jdbc.update("UPDATE users.github_account_links SET github_login=?,linked_at=?,access_token_ciphertext=?,access_token_expires_at=? WHERE user_id=? AND github_user_id=?",
                        identity.login(), Timestamp.from(now), encrypted, identity.expiresAt() == null ? null : Timestamp.from(identity.expiresAt()), pending.userId(), identity.id());
                }
                jdbc.update("DELETE FROM users.github_authorizations WHERE user_id=?", pending.userId());
                return new GitHubAccountLink(pending.userId(), identity.id(), identity.login(), now);
            });
        } catch (DuplicateKeyException conflict) { throw new GitHubLinkException(IDENTITY_IN_USE); }
    }
    public void discard(String hash) {
        jdbc.update("DELETE FROM users.github_authorizations WHERE state_hash=? AND consumed=TRUE", hash);
    }
    public Optional<GitHubAccountLink> find(UUID userId) {
        return jdbc.query("SELECT * FROM users.github_account_links WHERE user_id=?",
                (rs, row) -> new GitHubAccountLink(rs.getObject("user_id", UUID.class),
                        rs.getLong("github_user_id"), rs.getString("github_login"),
                        rs.getTimestamp("linked_at").toInstant()), userId).stream().findFirst();
    }
    public void unlink(UUID userId) {
        transactions.executeWithoutResult(tx -> {
            lockActiveUser(userId);
            jdbc.update("DELETE FROM users.github_authorizations WHERE user_id=?", userId);
            jdbc.update("DELETE FROM users.github_account_links WHERE user_id=?", userId);
        });
    }
    @Scheduled(fixedDelay = 60000)
    public void purgeExpired() {
        jdbc.update("DELETE FROM users.github_authorizations WHERE expires_at<=?", Timestamp.from(Instant.now()));
    }
}
