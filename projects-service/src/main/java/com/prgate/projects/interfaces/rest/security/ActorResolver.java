package com.prgate.projects.interfaces.rest.security;

import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.port.out.CognitoIdentityDirectory;
import com.prgate.projects.application.security.Actor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class ActorResolver {
    private final CognitoIdentityDirectory identities;
    public ActorResolver(CognitoIdentityDirectory identities) { this.identities = identities; }
    public Actor resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authenticated identity is required");
        }
        if (authentication.getPrincipal() instanceof Actor actor) { return actor; }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return identities.findByCognitoSub(jwt.getSubject()).map(user -> new Actor(user.id()))
                    .orElseThrow(() -> new AccessDeniedException("Cognito identity has no registered PRGate account"));
        }
        throw new AccessDeniedException("Unsupported authenticated identity");
    }
}
