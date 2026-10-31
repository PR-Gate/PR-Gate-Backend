package com.prgate.projects.interfaces.rest.error;

import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.exception.CourseNotFoundException;
import com.prgate.projects.application.exception.EnrollmentConflictException;
import com.prgate.projects.application.exception.EnrollmentNotFoundException;
import com.prgate.projects.application.exception.GitHubDirectoryException;
import com.prgate.projects.application.exception.GitHubOrganizationNotFoundException;
import com.prgate.projects.application.exception.ProjectConflictException;
import com.prgate.projects.application.exception.ProjectMembershipConflictException;
import com.prgate.projects.application.exception.ProjectNotFoundException;
import com.prgate.projects.application.exception.UserDirectoryException;
import com.prgate.projects.application.exception.UserNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> denied(AccessDeniedException error) { return problem(HttpStatus.FORBIDDEN,error.getMessage()); }
    @ExceptionHandler({CourseNotFoundException.class, ProjectNotFoundException.class, EnrollmentNotFoundException.class,
            UserNotFoundException.class, GitHubOrganizationNotFoundException.class})
    ResponseEntity<ProblemDetail> missing(RuntimeException error) { return problem(HttpStatus.NOT_FOUND,error.getMessage()); }
    @ExceptionHandler({EnrollmentConflictException.class, ProjectMembershipConflictException.class, ProjectConflictException.class, IllegalStateException.class})
    ResponseEntity<ProblemDetail> conflict(RuntimeException error) { return problem(HttpStatus.CONFLICT,error.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalid(IllegalArgumentException error) { return problem(HttpStatus.BAD_REQUEST,error.getMessage()); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class, ConstraintViolationException.class})
    ResponseEntity<ProblemDetail> malformed(Exception error) { return problem(HttpStatus.BAD_REQUEST,"Invalid request fields, parameters or JSON"); }
    @ExceptionHandler({UserDirectoryException.class, GitHubDirectoryException.class})
    ResponseEntity<ProblemDetail> unavailable(RuntimeException error) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE,"An external dependency is unavailable or returned an invalid response");
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception error) {
        LOG.error("Unhandled API failure: {}",error.getClass().getName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,"An unexpected error occurred");
    }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status,detail));
    }
}
