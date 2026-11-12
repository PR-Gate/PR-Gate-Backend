package com.prgate.users.infraestructure.identity;

import com.prgate.users.interfaces.rest.github.GitHubActorResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.*;
import jakarta.servlet.http.*;
import java.util.*;

@Configuration
@ConditionalOnProperty(name = "prgate.auth.provider", havingValue = "keycloak")
public class KeycloakIdentityConfiguration implements WebMvcConfigurer {
    @Bean
    JwtDecoder keycloakDecoder(@Value("${prgate.auth.issuer}") String issuer,
                               @Value("${prgate.auth.jwk-set-uri}") String jwks,
                               @Value("${prgate.auth.audience:prgate-api}") String audience,
                               @Value("${prgate.auth.client-id:prgate-frontend}") String client) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks)
                .jwsAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                jwt -> jwt.getExpiresAt() != null && jwt.getIssuer() != null
                        && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                        && jwt.getSubject().length() <= 255 && issuer.length() <= 512
                        && jwt.getAudience() != null && jwt.getAudience().contains(audience)
                        && client.equals(jwt.getClaimAsString("azp"))
                        && "Bearer".equals(jwt.getClaimAsString("typ"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        return decoder;
    }
    @Bean
    GitHubActorResolver keycloakGitHubActor(KeycloakIdentityService identities) {
        return request -> identities.current(request).getId();
    }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Legacy public user writes cannot assign roles or forge identity in this mode.
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "Use authenticated self-service endpoints");
            }
        }).addPathPatterns("/users", "/users/**");
    }
}
