package dev.elianhidalgo.portfolio.publishing.web;

import dev.elianhidalgo.portfolio.publishing.application.PublicationStatus;
import dev.elianhidalgo.portfolio.publishing.application.PublishTooSoonException;
import dev.elianhidalgo.portfolio.publishing.application.PublishingNotConfiguredException;
import dev.elianhidalgo.portfolio.publishing.application.PublishingService;
import dev.elianhidalgo.portfolio.publishing.application.RebuildFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Publicar" in the admin panel. Protected by the {@code /api/admin/**} security rule. */
@RestController
@RequestMapping("/api/admin/publish")
class PublishController {

    private final PublishingService publishing;

    PublishController(PublishingService publishing) {
        this.publishing = publishing;
    }

    @PostMapping
    ResponseEntity<PublicationStatus> publish() {
        return ResponseEntity.accepted().body(publishing.publish());
    }

    @GetMapping("/status")
    PublicationStatus status() {
        return publishing.status();
    }

    @ExceptionHandler(PublishTooSoonException.class)
    ResponseEntity<ProblemDetail> tooSoon(PublishTooSoonException exception) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
    }

    @ExceptionHandler(PublishingNotConfiguredException.class)
    ResponseEntity<ProblemDetail> notConfigured(PublishingNotConfiguredException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    /** The provider's details stay in the log; the admin gets one clear message. */
    @ExceptionHandler(RebuildFailedException.class)
    ResponseEntity<ProblemDetail> failed(RebuildFailedException exception) {
        return problem(HttpStatus.BAD_GATEWAY, "The site could not be rebuilt");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
