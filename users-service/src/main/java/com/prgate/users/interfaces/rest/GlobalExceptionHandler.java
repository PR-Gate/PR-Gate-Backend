package com.prgate.users.interfaces.rest;

import com.prgate.users.domain.exception.InvalidUserStateException;
import com.prgate.users.domain.exception.UserNotFoundException;
import com.prgate.users.interfaces.rest.resources.ApiErrorResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResource> handleUserNotFound(UserNotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResource> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return build(HttpStatus.BAD_REQUEST, "invalid value for parameter '" + exception.getName() + "'");
    }

    @ExceptionHandler({InvalidUserStateException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResource> handleBadRequest(Exception exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private static ResponseEntity<ApiErrorResource> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResource(status.value(), status.getReasonPhrase(), message, Instant.now()));
    }
}
