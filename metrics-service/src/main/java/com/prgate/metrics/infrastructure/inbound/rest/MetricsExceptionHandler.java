package com.prgate.metrics.infrastructure.inbound.rest;

import com.prgate.metrics.application.service.EvaluationAccessDenied;
import com.prgate.metrics.application.service.RubricEvaluationService.CourseRubricConflict;
import com.prgate.metrics.domain.exception.DomainRuleViolation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class MetricsExceptionHandler {
    @ExceptionHandler(EvaluationAccessDenied.class)
    ProblemDetail denied() { return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Project evaluation access denied"); }
    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail missing() { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Evaluation resource not found"); }
    @ExceptionHandler({DomainRuleViolation.class, IllegalArgumentException.class})
    ProblemDetail invalid(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict() { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Resource version or active repository configuration conflicts"); }
    @ExceptionHandler(CourseRubricConflict.class)
    ProblemDetail courseRubricConflict() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "El curso ya tiene una rúbrica técnica vigente.");
    }
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail unavailable() { return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Evaluation dependency unavailable"); }
}
