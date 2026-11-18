package com.prgate.metrics.infrastructure.inbound.rest;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/** The trusted backend resolves the user identity; this service key must never reach a browser. */
@Component
public class MetricsInternalSecurity extends OncePerRequestFilter {
    private final String token;
    public MetricsInternalSecurity(@Value("${app.api.token:}") String token) { this.token = token; }
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/internal/");
    }
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (token.isBlank() || auth == null || !auth.startsWith("Bearer ")
                || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                auth.substring(7).getBytes(StandardCharsets.UTF_8))) {
            response.sendError(token.isBlank() ? 503 : 401); return;
        }
        try {
            String actor = request.getHeader("X-Actor-Id");
            UUID id = UUID.fromString(actor);
            if (!id.toString().equalsIgnoreCase(actor)) throw new IllegalArgumentException();
            request.setAttribute("verifiedActorId", id);
        } catch (IllegalArgumentException | NullPointerException exception) {
            response.sendError(401); return;
        }
        chain.doFilter(request, response);
    }
}
