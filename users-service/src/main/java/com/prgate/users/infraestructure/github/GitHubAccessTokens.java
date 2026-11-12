package com.prgate.users.infraestructure.github;
import com.prgate.users.application.github.GitHubLinkException;
import com.prgate.users.domain.model.GitHubAccountLink;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import static com.prgate.users.application.github.GitHubLinkException.Reason.REAUTHORIZATION_REQUIRED;
@Service
@ConditionalOnProperty(name="github.enabled", havingValue="true")
public class GitHubAccessTokens {
    private final JdbcTemplate jdbc;
    private final GitHubTokenCipher cipher;
    public GitHubAccessTokens(JdbcTemplate jdbc, GitHubTokenCipher cipher) { this.jdbc=jdbc; this.cipher=cipher; }
    public String accessToken(GitHubAccountLink link) {
        return jdbc.query("SELECT access_token_ciphertext,access_token_expires_at FROM users.github_account_links WHERE user_id=? AND github_user_id=?",
            (rs, row) -> {
                String encrypted = rs.getString("access_token_ciphertext");
                var expiry = rs.getTimestamp("access_token_expires_at");
                if (encrypted == null || (expiry != null && !expiry.toInstant().isAfter(Instant.now().plusSeconds(30))))
                    throw new GitHubLinkException(REAUTHORIZATION_REQUIRED);
                return cipher.decrypt(encrypted, link.userId() + ":" + link.githubUserId());
            }, link.userId(), link.githubUserId()).stream().findFirst()
            .orElseThrow(() -> new GitHubLinkException(REAUTHORIZATION_REQUIRED));
    }
}
