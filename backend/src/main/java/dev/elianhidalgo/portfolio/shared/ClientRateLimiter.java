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
import java.util.function.LongSupplier;

/**
 * A token bucket per client, in memory (the app runs as one instance).
 *
 * <p>Each client may spend {@code capacity} requests, refilled gradually over {@code period}.
 * Memory stays bounded: a client is forgotten once idle for 10 minutes or one full {@code period},
 * whichever is longer (checked every 1,000 calls) — by then its bucket has refilled, so forgetting
 * it never hands out extra requests.
 */
public final class ClientRateLimiter {

    /** Minimum idle time before a client is forgotten. */
    private static final Duration IDLE_EVICTION = Duration.ofMinutes(10);
    private static final int SWEEP_EVERY = 1_000;

    private record Client(Bucket bucket, long lastSeenNanos) {}

    private final int capacity;
    private final Duration period;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final AtomicInteger callsSinceSweep = new AtomicInteger();
    private final LongSupplier nanoClock;
    private final long idleEvictionNanos;

    public ClientRateLimiter(int capacity, Duration period) {
        this(capacity, period, System::nanoTime);
    }

    /** For tests: the clock that measures idle time. */
    ClientRateLimiter(int capacity, Duration period, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.period = period;
        this.nanoClock = nanoClock;
        this.idleEvictionNanos = Math.max(IDLE_EVICTION.toNanos(), period.toNanos());
    }

    /** Empty when the request may proceed; otherwise the seconds to wait (for {@code Retry-After}, at least 1). */
    public OptionalLong tryConsume(String clientKey) {
        sweepIdleClients();
        long now = nanoClock.getAsLong();
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
        long cutoff = nanoClock.getAsLong() - idleEvictionNanos;
        clients.values().removeIf(client -> client.lastSeenNanos() < cutoff);
    }
}
