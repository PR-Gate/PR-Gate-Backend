package com.prgate.users.infraestructure.identity;

import com.prgate.users.domain.model.*;
import com.prgate.users.infraestructure.persistence.jpa.repositories.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
@ConditionalOnProperty(name = "prgate.auth.provider", havingValue = "keycloak")
public class KeycloakIdentityService {
    private final JwtDecoder decoder;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public KeycloakIdentityService(JwtDecoder decoder, UserRepository users, JdbcTemplate jdbc,
                                   PlatformTransactionManager transactions) {
        this.decoder = decoder; this.users = users; this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
    }
    public Jwt authenticate(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer "))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        try { return decoder.decode(header.substring(7)); }
        catch (JwtException | IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token");
        }
    }
    private Optional<User> find(Jwt jwt) {
        var ids = jdbc.query("SELECT user_id FROM users.external_identities WHERE issuer=? AND subject=?",
                (rs, row) -> rs.getObject("user_id", UUID.class), jwt.getIssuer().toString(), jwt.getSubject());
        return ids.isEmpty() ? Optional.empty() : users.findById(ids.getFirst());
    }
    private User active(User user) {
        if (user.getStatus() != UserStatus.ACTIVE)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ACADEMIC_PROFILE_INACTIVE");
        return user;
    }
    public User current(HttpServletRequest request) {
        var jwt = authenticate(request);
        return active(find(jwt).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ACADEMIC_PROFILE_REQUIRED")));
    }
    public User openSession(HttpServletRequest request) {
        return current(request);
    }
    public User registerProfile(HttpServletRequest request, String profile) {
        var jwt = authenticate(request);
        UserRole selected = switch (profile == null ? "" : profile) {
            case "student" -> UserRole.STUDENT;
            case "teacher" -> UserRole.TEACHER;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ACADEMIC_PROFILE_INVALID");
        };
        try {
            return tx.execute(status -> {
                var existing = find(jwt);
                if (existing.isPresent()) return active(existing.get());
                String first = jwt.getClaimAsString("given_name");
                String last = jwt.getClaimAsString("family_name");
                if (first == null || first.isBlank() || last == null || last.isBlank())
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Complete your name in Keycloak");
                Instant now = Instant.now();
                var candidate = User.registerExternal(UUID.randomUUID(), first, last, jwt.getClaimAsString("email"), now);
                candidate.changeRole(selected, now);
                var user = users.saveAndFlush(candidate);
                jdbc.update("INSERT INTO users.external_identities (issuer,subject,user_id,created_at) VALUES (?,?,?,?)",
                        jwt.getIssuer().toString(), jwt.getSubject(), user.getId(), java.sql.Timestamp.from(Instant.now()));
                return user;
            });
        } catch (DataIntegrityViolationException concurrent) {
            // A concurrent login may have provisioned the same issuer/subject.
            return active(find(jwt).orElseThrow(() -> concurrent));
        }
    }
}
