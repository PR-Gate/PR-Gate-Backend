package com.prgate.ai.infrastructure.inbound.rest;

import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class InternalApiSecurity implements WebMvcConfigurer {
    private final byte[] expected;
    public InternalApiSecurity(@Value("${prgate.internal.service-token}") String token) {
        if (token.isBlank()) throw new IllegalArgumentException("Configure INTERNAL_SERVICE_TOKEN");
        expected = token.getBytes(StandardCharsets.UTF_8);
    }
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws java.io.IOException {
                String auth = request.getHeader("Authorization");
                boolean valid = auth != null && auth.startsWith("Bearer ")
                        && MessageDigest.isEqual(expected, auth.substring(7).getBytes(StandardCharsets.UTF_8));
                if (valid) return true;
                response.setStatus(401);
                response.setHeader("WWW-Authenticate", "Bearer");
                response.setContentType("application/problem+json");
                response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\",\"detail\":\"Service credentials required\"}");
                return false;
            }
        }).addPathPatterns("/api/**");
    }
}
