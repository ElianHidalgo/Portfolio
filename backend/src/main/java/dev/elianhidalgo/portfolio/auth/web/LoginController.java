package dev.elianhidalgo.portfolio.auth.web;

import dev.elianhidalgo.portfolio.auth.application.InvalidCredentialsException;
import dev.elianhidalgo.portfolio.auth.application.IssuedToken;
import dev.elianhidalgo.portfolio.auth.application.LoginService;
import dev.elianhidalgo.portfolio.shared.ClientAddress;
import dev.elianhidalgo.portfolio.shared.ClientRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.OptionalLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin sign-in. Attempts are limited per client before the password is even checked, so
 * guessing is slow; request fields are never logged.
 */
@RestController
class LoginController {

    private final LoginService loginService;
    private final ClientRateLimiter attemptLimiter;

    LoginController(LoginService loginService,
            @Value("${app.auth.login.attempts:5}") int attempts,
            @Value("${app.auth.login.window:PT15M}") Duration window) {
        this.loginService = loginService;
        this.attemptLimiter = new ClientRateLimiter(attempts, window);
    }

    @PostMapping("/api/auth/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        OptionalLong retryAfterSeconds = attemptLimiter.tryConsume(ClientAddress.of(httpRequest));
        if (retryAfterSeconds.isPresent()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds.getAsLong()))
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                            "Too many login attempts, try again later"));
        }
        IssuedToken token = loginService.login(request.username(), request.password());
        return ResponseEntity.ok(token);
    }

    /** 401 without WWW-Authenticate: this is a form answer, not a challenge for a bearer token. */
    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ProblemDetail> invalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage()));
    }
}
