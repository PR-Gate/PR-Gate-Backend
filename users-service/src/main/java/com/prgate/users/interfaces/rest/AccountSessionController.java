package com.prgate.users.interfaces.rest;

import com.prgate.users.infraestructure.identity.KeycloakIdentityService;
import com.prgate.users.domain.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "prgate.auth.provider", havingValue = "keycloak")
public class AccountSessionController {
    private final KeycloakIdentityService identities;
    public AccountSessionController(KeycloakIdentityService identities) { this.identities = identities; }
    @PostMapping("/api/me/session")
    public ResponseEntity<Profile> open(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Profile.from(identities.openSession(request)));
    }
    @GetMapping("/api/me")
    public ResponseEntity<Profile> current(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Profile.from(identities.current(request)));
    }
    @PostMapping("/api/me/registration")
    public ResponseEntity<Profile> register(HttpServletRequest request, @RequestBody Registration registration) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Profile.from(identities.registerProfile(request, registration.profile())));
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> failure(org.springframework.web.server.ResponseStatusException failure) {
        String code = switch (failure.getReason() == null ? "" : failure.getReason()) {
            case "ACADEMIC_PROFILE_REQUIRED", "ACADEMIC_PROFILE_INACTIVE", "ACADEMIC_PROFILE_INVALID" -> failure.getReason();
            default -> failure.getStatusCode().value() == 401 ? "AUTHENTICATION_REQUIRED" : "PROFILE_UNAVAILABLE";
        };
        var problem = ProblemDetail.forStatusAndDetail(failure.getStatusCode(), code);
        problem.setProperty("code", code);
        return ResponseEntity.status(failure.getStatusCode()).cacheControl(CacheControl.noStore()).body(problem);
    }
    public record Registration(String profile) {}
    public record Profile(UUID id, String name, String email, String role, String status) {
        static Profile from(User user) {
            return new Profile(user.getId(), user.getFirstName() + " " + user.getLastName(),
                    user.getContactEmail(), user.getRole().name(), user.getStatus().name());
        }
    }
}
