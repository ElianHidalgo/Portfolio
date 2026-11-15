package dev.elianhidalgo.portfolio.shared.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's default /error body with RFC 9457 problem JSON, so errors raised
 * outside controllers (filters, the container) look like every other API error. A direct
 * request to /error has no error attached and answers 404.
 */
@RestController
public class ProblemErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = code instanceof Integer value && HttpStatus.resolve(value) != null
                ? HttpStatus.valueOf(value)
                : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(ProblemDetail.forStatus(status));
    }
}
