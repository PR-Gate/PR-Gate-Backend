package com.prgate.users.interfaces.rest.github;
import com.prgate.users.application.github.GitHubLinkException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestControllerAdvice(assignableTypes = {GitHubLinkController.class, GitHubRepositoriesController.class})
public class GitHubLinkExceptionHandler {
    @ExceptionHandler(GitHubLinkException.class)
    ResponseEntity<ProblemDetail> handle(GitHubLinkException error) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(error.reason().status()), error.reason().name());
        detail.setProperty("code", "GITHUB_" + error.reason().name());
        return ResponseEntity.status(error.reason().status()).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").body(detail);
    }
}
