package com.prgate.users.infraestructure.github;
import com.prgate.users.application.github.*;
import com.prgate.users.interfaces.rest.github.GitHubActorResolver;
import com.prgate.users.infraestructure.persistence.jpa.repositories.UserRepository;
import com.prgate.users.domain.model.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.client.RestClient;
import java.net.URI;
import java.time.Clock;
import java.util.*;
import static com.prgate.users.application.github.GitHubLinkException.Reason.*;
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "github.enabled", havingValue = "true")
public class GitHubLinkConfiguration {
    @Bean
    GitHubIdentityProvider gitHubIdentityProvider(RestClient.Builder builder,
            @Value("${github.client-id:}") String clientId, @Value("${github.client-secret:}") String secret,
            @Value("${github.redirect-uri:}") String redirect, Environment environment) {
        if (clientId.isBlank() || secret.isBlank()) throw new IllegalStateException("Configure GitHub OAuth credentials");
        URI uri = URI.create(redirect);
        boolean local = environment.matchesProfiles("local");
        if (uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null
                || (!"https".equals(uri.getScheme()) && !(local && "http".equals(uri.getScheme())
                && Set.of("localhost", "127.0.0.1").contains(uri.getHost()))))
            throw new IllegalStateException("GitHub redirect must use HTTPS, or localhost HTTP in local profile");
        if (!"/api/integrations/github/callback".equals(uri.getPath()))
            throw new IllegalStateException("GitHub redirect path must match the callback endpoint");
        return new HttpGitHubIdentityProvider(builder, clientId, secret, redirect);
    }
    @Bean
    GitHubTokenCipher gitHubTokenCipher(@Value("${github.token-encryption-key:}") String key) {
        return new GitHubTokenCipher(key);
    }
    @Bean
    GitHubLinkStore gitHubLinkStore(JdbcTemplate jdbc, PlatformTransactionManager manager, GitHubTokenCipher cipher) {
        return new JdbcGitHubLinkStore(jdbc, new TransactionTemplate(manager), cipher);
    }
    @Bean
    GitHubLinkService gitHubLinkService(GitHubLinkStore store, GitHubIdentityProvider provider) {
        return new GitHubLinkService(store, provider, Clock.systemUTC());
    }
    @Bean
    @Profile("local")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("'${prgate.auth.provider:cognito}' != 'keycloak'")
    GitHubActorResolver localGitHubActor(UserRepository users) {
        return request -> {
            try {
                String value = request.getHeader("X-User-Id");
                UUID id = UUID.fromString(value);
                if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
                return users.findById(id).filter(u -> u.getStatus() == UserStatus.ACTIVE)
                        .orElseThrow(() -> new GitHubLinkException(USER_UNAVAILABLE)).getId();
            } catch (IllegalArgumentException | NullPointerException invalid) {
                throw new GitHubLinkException(AUTHENTICATION_REQUIRED);
            }
        };
    }
    @Bean
    @Profile("!local")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("'${prgate.auth.provider:cognito}' != 'keycloak'")
    GitHubActorResolver cognitoGitHubActor(UserRepository users,
            @Value("${github.cognito-issuer:}") String issuer,
            @Value("${github.cognito-client-id:}") String clientId) {
        if (!issuer.startsWith("https://") || clientId.isBlank())
            throw new IllegalStateException("Configure Cognito issuer and client ID for GitHub linking");
        var decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return new CognitoGitHubActorResolver(users, decoder, clientId);
    }
}
