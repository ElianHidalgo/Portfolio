package dev.elianhidalgo.portfolio.contact.web;

import dev.elianhidalgo.portfolio.contact.application.ContactService;
import dev.elianhidalgo.portfolio.contact.application.InboxFullException;
import dev.elianhidalgo.portfolio.contact.application.NewContactMessage;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives the public contact form. Every outcome a sender may see is 202 (stored, or
 * silently dropped when the honeypot is filled), 400 (invalid) or 429 (too many messages).
 * Request fields are never logged.
 */
@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final ContactService contactService;
    private final ClientRateLimiter perClientLimiter;

    public ContactController(ContactService contactService,
            @Value("${app.contact.per-client-per-hour:5}") int perClientPerHour) {
        this.contactService = contactService;
        this.perClientLimiter = new ClientRateLimiter(perClientPerHour, Duration.ofHours(1));
    }

    @PostMapping
    public ResponseEntity<?> send(@Valid @RequestBody ContactRequest request, HttpServletRequest httpRequest) {
        OptionalLong retryAfterSeconds = perClientLimiter.tryConsume(ClientAddress.of(httpRequest));
        if (retryAfterSeconds.isPresent()) {
            return tooManyRequests("Too many messages, try again later", retryAfterSeconds.getAsLong());
        }
        // A bot gets the same answer as a person, so it cannot learn that it was detected.
        if (!request.isFromBot()) {
            contactService.submit(new NewContactMessage(request.name(), request.email(), request.message(), request.locale()));
        }
        return ResponseEntity.accepted().build();
    }

    @ExceptionHandler(InboxFullException.class)
    ResponseEntity<ProblemDetail> inboxFull(InboxFullException exception) {
        return tooManyRequests(exception.getMessage(), 3600);
    }

    private static ResponseEntity<ProblemDetail> tooManyRequests(String detail, long retryAfterSeconds) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, detail));
    }
}
