package dev.elianhidalgo.portfolio.shared;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A token bucket per client, in memory (the app runs as one instance).
 *
 * <p>Each client may spend {@code capacity} requests, refilled gradually over {@code period}.
 * Memory stays bounded: clients idle for 10 minutes are forgotten (checked every 1,000 calls).
 */
public final class ClientRateLimiter {

    private static final Duration IDLE_EVICTION = Duration.ofMinutes(10);
    private static final int SWEEP_EVERY = 1_000;

    private record Client(Bucket bucket, long lastSeenNanos) {}

    private final int capacity;
    private final Duration period;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final AtomicInteger callsSinceSweep = new AtomicInteger();

    public ClientRateLimiter(int capacity, Duration period) {
        this.capacity = capacity;
        this.period = period;
    }

    /** Empty when the request may proceed; otherwise the seconds to wait (for {@code Retry-After}, at least 1). */
    public OptionalLong tryConsume(String clientKey) {
        sweepIdleClients();
        long now = System.nanoTime();
        Client client = clients.compute(clientKey,
                (key, existing) -> new Client(existing != null ? existing.bucket() : newBucket(), now));

        ConsumptionProbe probe = client.bucket().tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1));
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build())
                .build();
    }

    private void sweepIdleClients() {
        if (callsSinceSweep.incrementAndGet() < SWEEP_EVERY) {
            return;
        }
        callsSinceSweep.set(0);
        long cutoff = System.nanoTime() - IDLE_EVICTION.toNanos();
        clients.values().removeIf(client -> client.lastSeenNanos() < cutoff);
    }
}
