package dev.elianhidalgo.portfolio.shared.web;

import dev.elianhidalgo.portfolio.shared.ClientAddress;
import dev.elianhidalgo.portfolio.shared.ClientRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.OptionalLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits API requests per client (see {@link ClientRateLimiter} and {@link ClientAddress}
 * for how clients are counted and identified). Health checks are exempt.
 */
@Component
// Runs after ApiHeadersFilter and SiteCorsFilter, so even a 429 carries the security and CORS headers.
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ClientRateLimiter limiter;

    public RateLimitFilter(@Value("${app.rate-limit.requests-per-minute:60}") int requestsPerMinute) {
        this.limiter = new ClientRateLimiter(requestsPerMinute, Duration.ofMinutes(1));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith("/api/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        OptionalLong retryAfterSeconds = limiter.tryConsume(ClientAddress.of(request));
        if (retryAfterSeconds.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds.getAsLong()));
        // A rejection must never be cached, even on otherwise cacheable public paths.
        response.setHeader("Cache-Control", "no-store");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Too many requests\",\"instance\":\"" + request.getRequestURI().replace("\"", "") + "\"}");
    }
}
