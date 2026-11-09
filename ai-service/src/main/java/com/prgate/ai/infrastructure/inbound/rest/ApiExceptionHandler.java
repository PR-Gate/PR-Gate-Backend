package com.prgate.ai.infrastructure.inbound.rest;

import com.prgate.ai.application.exception.*;
import com.prgate.ai.application.port.out.*;
import com.prgate.ai.domain.exception.DomainRuleViolation;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public final class ApiExceptionHandler {
    @ExceptionHandler({TechnicalPlanNotFoundException.class, AssessmentNotFoundException.class})
    ProblemDetail missing(RuntimeException ex) { return problem(404, "Resource not found"); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ProblemDetail malformed(Exception ex) { return problem(400, "Invalid request fields or JSON"); }
    @ExceptionHandler(DomainRuleViolation.class)
    ProblemDetail domain(DomainRuleViolation ex) { return problem(422, ex.getMessage()); }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail conflict(Exception ex) { return problem(409, "Resource changed; reload before retrying"); }
    @ExceptionHandler(AiGenerationException.class)
    ProblemDetail ai(AiGenerationException ex) {
        var result = problem(502, ex.reason().detail());
        result.setProperty("provider", ex.provider().name().toLowerCase(java.util.Locale.ROOT));
        result.setProperty("code", ex.code());
        if (ex.upstreamStatus() != null) result.setProperty("upstreamStatus", ex.upstreamStatus());
        return result;
    }
    @ExceptionHandler(RubricTranslationException.class)
    ProblemDetail rubric(RubricTranslationException ex) {
        var result = problem(502, ex.reason().detail());
        result.setProperty("code", ex.reason().code());
        if (ex.upstreamStatus() != null) result.setProperty("upstreamStatus", ex.upstreamStatus());
        return result;
    }
    @ExceptionHandler({FeedbackGenerationException.class,
            InvalidTechnicalProposalException.class})
    ProblemDetail provider(Exception ex) { return problem(502, "AI generation failed; no successful result was accepted"); }
    @ExceptionHandler({CatalogUnavailableException.class, DataAccessException.class})
    ProblemDetail unavailable(Exception ex) { return problem(503, "Required backend dependency is unavailable"); }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) { return problem(500, "Could not complete the operation"); }
    private static ProblemDetail problem(int status, String detail) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    }
}
