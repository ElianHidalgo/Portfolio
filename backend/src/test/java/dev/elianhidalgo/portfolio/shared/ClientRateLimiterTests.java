package dev.elianhidalgo.portfolio.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientRateLimiterTests {

    @Test
    void allowsUpToCapacityThenRefusesWithRetryAfter() {
        var limiter = new ClientRateLimiter(2, Duration.ofHours(1));

        assertThat(limiter.tryConsume("a")).isEmpty();
        assertThat(limiter.tryConsume("a")).isEmpty();
        var refused = limiter.tryConsume("a");
        assertThat(refused).isPresent();
        assertThat(refused.getAsLong()).isBetween(1L, 3600L);
    }

    @Test
    void clientsHaveSeparateBuckets() {
        var limiter = new ClientRateLimiter(1, Duration.ofHours(1));
        limiter.tryConsume("a");

        assertThat(limiter.tryConsume("b")).isEmpty();
    }

    @Test
    void clientAddressPrefersCloudflareHeaderAndIgnoresForwardedFor() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.9");
        assertThat(ClientAddress.of(request)).isEqualTo("10.0.0.1");

        request.addHeader("CF-Connecting-IP", "203.0.113.7");
        assertThat(ClientAddress.of(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void idleClientIsNotForgottenBeforeItsBucketRefills() {
        var nanos = new AtomicLong();
        var limiter = new ClientRateLimiter(1, Duration.ofHours(1), nanos::get);
        limiter.tryConsume("a");

        nanos.addAndGet(Duration.ofMinutes(11).toNanos());
        for (int i = 0; i < 1_000; i++) {
            limiter.tryConsume("other-" + i); // enough calls to trigger an idle sweep
        }

        assertThat(limiter.tryConsume("a")).isPresent();
    }
}
