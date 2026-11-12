package com.prgate.users.infraestructure.github;

import com.prgate.users.application.github.GitHubLinkException;
import com.prgate.users.domain.model.UserStatus;
import com.prgate.users.infraestructure.persistence.jpa.repositories.UserRepository;
import com.prgate.users.interfaces.rest.github.GitHubActorResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.jwt.*;
import java.util.UUID;
import static com.prgate.users.application.github.GitHubLinkException.Reason.*;

public final class CognitoGitHubActorResolver implements GitHubActorResolver {
    private final UserRepository users;
    private final JwtDecoder decoder;
    private final String clientId;
    public CognitoGitHubActorResolver(UserRepository users, JwtDecoder decoder, String clientId) {
        this.users = users; this.decoder = decoder; this.clientId = clientId;
    }
    public UUID resolve(HttpServletRequest request) {
        try {
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) throw new GitHubLinkException(AUTHENTICATION_REQUIRED);
            var jwt = decoder.decode(header.substring(7));
            if (jwt.getExpiresAt() == null || jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || !"access".equals(jwt.getClaimAsString("token_use"))
                    || !clientId.equals(jwt.getClaimAsString("client_id")))
                throw new GitHubLinkException(AUTHENTICATION_REQUIRED);
            return users.findByCognitoSub(jwt.getSubject()).filter(u -> u.getStatus() == UserStatus.ACTIVE)
                    .orElseThrow(() -> new GitHubLinkException(USER_UNAVAILABLE)).getId();
        } catch (JwtException | IllegalArgumentException invalid) {
            throw new GitHubLinkException(AUTHENTICATION_REQUIRED);
        }
    }
}
