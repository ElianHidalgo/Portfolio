package dev.elianhidalgo.portfolio.shared.web;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits API requests per client (token bucket, in memory — the app runs as one instance).
 *
 * <p>The client is identified by {@code CF-Connecting-IP}: Render sits behind Cloudflare,
 * which sets that header to the real visitor address and overwrites any value a client
 * sends. {@code X-Forwarded-For} is never trusted, since a client can put anything in it.
 * Without Cloudflare (local runs) the socket address is used.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration IDLE_EVICTION = Duration.ofMinutes(10);
    private static final int SWEEP_EVERY = 1_000;

    private record Client(Bucket bucket, long lastSeenNanos) {}

    private final int requestsPerMinute;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final AtomicInteger requestsSinceSweep = new AtomicInteger();

    public RateLimitFilter(@Value("${app.rate-limit.requests-per-minute:60}") int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith("/api/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        sweepIdleClients();
        long now = System.nanoTime();
        Client client = clients.compute(clientKey(request),
                (key, existing) -> new Client(existing != null ? existing.bucket() : newBucket(), now));

        ConsumptionProbe probe = client.bucket().tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Too many requests\",\"instance\":\"" + request.getRequestURI().replace("\"", "") + "\"}");
    }

    private static String clientKey(HttpServletRequest request) {
        String cloudflareClientIp = request.getHeader("CF-Connecting-IP");
        return cloudflareClientIp != null && !cloudflareClientIp.isBlank() ? cloudflareClientIp : request.getRemoteAddr();
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /** Bounded memory: forget clients idle for 10 minutes (checked every 1,000 requests). */
    private void sweepIdleClients() {
        if (requestsSinceSweep.incrementAndGet() < SWEEP_EVERY) {
            return;
        }
        requestsSinceSweep.set(0);
        long cutoff = System.nanoTime() - IDLE_EVICTION.toNanos();
        clients.values().removeIf(client -> client.lastSeenNanos() < cutoff);
    }
}
