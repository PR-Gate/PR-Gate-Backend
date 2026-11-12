package com.prgate.users.interfaces.rest.github;
import com.prgate.users.application.github.*;
import com.prgate.users.domain.model.GitHubAccountLink;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.*;
@RestController
@ConditionalOnProperty(name = "github.enabled", havingValue = "true")
@Tag(name = "GitHub linking", description = "Identidad y autorizacion de repositorios de GitHub")
public class GitHubLinkController {
    static final String COOKIE = "prgate_github_link";
    private static final String COOKIE_PATH = "/api/integrations/github/callback";
    private final GitHubLinkService service;
    private final GitHubActorResolver actors;
    private final boolean local;
    public GitHubLinkController(GitHubLinkService service, GitHubActorResolver actors, Environment env) {
        this.service = service; this.actors = actors; this.local = env.matchesProfiles("local");
    }
    @PostMapping("/api/me/integrations/github/authorization")
    @Operation(summary = "Iniciar vinculacion", description = "Cognito Bearer; solo perfil local permite X-User-Id")
    @SecurityRequirement(name = "githubActor")
    public ResponseEntity<AuthorizationResponse> begin(HttpServletRequest request) {
        var auth = service.begin(actors.resolve(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(auth.browserSecret(), Duration.ofMinutes(10)))
                .body(new AuthorizationResponse(auth.authorizationUrl().toString(), auth.expiresAt()));
    }
    @GetMapping("/api/integrations/github/callback")
    @Operation(summary = "Callback OAuth", description = "Abrir mediante el flujo del navegador; no ejecutar manualmente en Swagger")
    public ResponseEntity<GitHubAccountLink> callback(
            @RequestParam(required = false) String state, @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @CookieValue(name = COOKIE, required = false) String browser) {
        var result = service.complete(state, browser, code, error);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
                .header("Referrer-Policy", "no-referrer").body(result);
    }
    @GetMapping("/api/me/integrations/github")
    @SecurityRequirement(name = "githubActor")
    public ResponseEntity<GitHubAccountLink> get(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(actors.resolve(request)));
    }
    @DeleteMapping("/api/me/integrations/github")
    @SecurityRequirement(name = "githubActor")
    public ResponseEntity<Void> unlink(HttpServletRequest request) {
        service.unlink(actors.resolve(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO)).build();
    }
    private String cookie(String value, Duration age) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(!local)
                .sameSite("Lax").path(COOKIE_PATH).maxAge(age).build().toString();
    }
    public record AuthorizationResponse(String authorizationUrl, Instant expiresAt) {
        @Override public String toString() { return "AuthorizationResponse[REDACTED]"; }
    }
}
