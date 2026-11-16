package dev.elianhidalgo.portfolio.auth.web;

import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Who is signed in and until when: lets the admin panel confirm its token is still accepted. */
@RestController
class SessionController {

    record SessionResponse(String username, Instant expiresAt) {}

    @GetMapping("/api/admin/session")
    SessionResponse session(@AuthenticationPrincipal Jwt jwt) {
        return new SessionResponse(jwt.getSubject(), jwt.getExpiresAt());
    }
}
