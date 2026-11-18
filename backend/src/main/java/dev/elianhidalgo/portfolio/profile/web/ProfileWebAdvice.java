package dev.elianhidalgo.portfolio.profile.web;

import dev.elianhidalgo.portfolio.profile.application.DuplicateSkillNameException;
import dev.elianhidalgo.portfolio.profile.application.InvalidSkillOrderException;
import dev.elianhidalgo.portfolio.profile.application.SkillNotFoundException;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Problem JSON for the admin profile and skills endpoints; validation errors name the field. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {AdminProfileController.class, AdminSkillsController.class})
class ProfileWebAdvice {

    @ExceptionHandler(SkillNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(SkillNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(DuplicateSkillNameException.class)
    ResponseEntity<ProblemDetail> duplicate(DuplicateSkillNameException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidSkillOrderException.class)
    ResponseEntity<ProblemDetail> invalidOrder(InvalidSkillOrderException exception) {
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
