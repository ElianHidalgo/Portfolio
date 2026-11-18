package dev.elianhidalgo.portfolio.projects.web;

import dev.elianhidalgo.portfolio.projects.application.DuplicateSlugException;
import dev.elianhidalgo.portfolio.projects.application.InvalidProjectOrderException;
import dev.elianhidalgo.portfolio.projects.application.LastPublishedProjectException;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Problem JSON for the admin projects endpoints; validation errors name the field. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AdminProjectsController.class)
class ProjectsWebAdvice {

    @ExceptionHandler(DuplicateSlugException.class)
    ResponseEntity<ProblemDetail> duplicate(DuplicateSlugException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(LastPublishedProjectException.class)
    ResponseEntity<ProblemDetail> lastPublished(LastPublishedProjectException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidProjectOrderException.class)
    ResponseEntity<ProblemDetail> invalidOrder(InvalidProjectOrderException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, detail.isEmpty() ? "Invalid request content." : detail);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
