package com.prgate.users.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalServiceAuthInterceptor implements HandlerInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedToken;

    public InternalServiceAuthInterceptor(@Value("${prgate.internal.service-token:}") String expectedToken) {
        if (expectedToken == null || expectedToken.isBlank()) {
            throw new IllegalStateException(
                    "prgate.internal.service-token must be configured for internal service authentication");
        }
        this.expectedToken = expectedToken.trim().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            reject(response, HttpStatus.UNAUTHORIZED, "missing or malformed service credentials");
            return false;
        }

        byte[] presentedToken = authorization.substring(BEARER_PREFIX.length()).trim()
                .getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(presentedToken, expectedToken)) {
            reject(response, HttpStatus.FORBIDDEN, "service is not authorized for internal queries");
            return false;
        }

        return true;
    }

    private static void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"message\":\"" + message + "\"}");
    }
}
