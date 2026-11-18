package com.prgate.projects.infrastructure.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Configuration
public class EvaluationServiceSecurity {
    @Bean
    @Order(0)
    SecurityFilterChain evaluationAccessSecurity(HttpSecurity http,
            @Value("${METRICS_PROJECTS_TOKEN:}") String token) throws Exception {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                String auth = request.getHeader("Authorization");
                if (token.isBlank() || auth == null || !auth.startsWith("Bearer ")
                        || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                        auth.substring(7).getBytes(StandardCharsets.UTF_8))) {
                    response.sendError(token.isBlank() ? 503 : 401);
                    return;
                }
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken("metrics-service", null, List.of()));
                SecurityContextHolder.setContext(context);
                chain.doFilter(request, response);
            }
        };
        return http.securityMatcher("/internal/v1/projects/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .addFilterBefore(filter, AnonymousAuthenticationFilter.class).build();
    }
}
