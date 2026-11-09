package com.prgate.projects.infrastructure.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CognitoTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final String clientId;
    public CognitoTokenValidator(String clientId) { this.clientId = clientId; }
    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (!"access".equals(token.getClaimAsString("token_use"))
                || !clientId.equals(token.getClaimAsString("client_id"))
                || token.getSubject() == null || token.getSubject().isBlank() || token.getExpiresAt() == null) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "A valid Cognito access token is required", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
