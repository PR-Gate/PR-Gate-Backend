package com.prgate.cicd.interfaces.http.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackageClasses = GitHubWebhookController.class)
public class WebhookExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<WebhookError> handleStatusException(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null ? "Webhook request failed" : exception.getReason();
        return ResponseEntity.status(status).body(new WebhookError(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<WebhookError> handleInvalidPayload(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new WebhookError("Webhook payload contains invalid data"));
    }

    public record WebhookError(String error) {
    }
}
