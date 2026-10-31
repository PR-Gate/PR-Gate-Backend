package com.prgate.projects.infrastructure.security;

import com.prgate.projects.application.security.Actor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** Explicit local development identity; never enabled by the default profile. */
@Configuration
@Profile("local")
public class LocalSecurityConfiguration {
    @Bean
    SecurityFilterChain localSecurity(HttpSecurity http, SecurityProblemHandler problems) throws Exception {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                String value = request.getHeader("X-User-Id");
                if (value != null) {
                    try {
                        UUID id = UUID.fromString(value);
                        if (!id.toString().equalsIgnoreCase(value)) { throw new IllegalArgumentException(); }
                        var context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(new UsernamePasswordAuthenticationToken(new Actor(id), null, List.of()));
                        SecurityContextHolder.setContext(context);
                    } catch (IllegalArgumentException failure) {
                        problems.commence(request, response, new BadCredentialsException("Invalid local UUID"));
                        return;
                    }
                }
                chain.doFilter(request, response);
            }
        };
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .addFilterBefore(filter, AnonymousAuthenticationFilter.class).build();
    }
}
