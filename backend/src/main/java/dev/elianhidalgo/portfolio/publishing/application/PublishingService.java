package dev.elianhidalgo.portfolio.publishing.application;

import dev.elianhidalgo.portfolio.publishing.domain.Publication;
import dev.elianhidalgo.portfolio.publishing.domain.PublicationRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * "Publicar": asks the provider to rebuild the public site and records accepted requests.
 * A second request within a minute is refused — Cloudflare is still building the first one.
 */
@Service
public class PublishingService {

    private static final Logger log = LoggerFactory.getLogger(PublishingService.class);
    static final Duration MIN_INTERVAL = Duration.ofMinutes(1);

    private final SiteRebuildTrigger trigger;
    private final PublicationRepository publications;

    public PublishingService(SiteRebuildTrigger trigger, PublicationRepository publications) {
        this.trigger = trigger;
        this.publications = publications;
    }

    /** Synchronized: one instance serves the API, so this makes the one-minute guard exact. */
    public synchronized PublicationStatus publish() {
        Instant now = Instant.now();
        publications.findFirstByOrderByRequestedAtDesc()
                .filter(last -> last.getRequestedAt().isAfter(now.minus(MIN_INTERVAL)))
                .ifPresent(last -> {
                    throw new PublishTooSoonException();
                });
        // Outside any transaction: the outbound call must not hold a database connection.
        String buildId = trigger.trigger().orElse(null);
        publications.save(new Publication(now, buildId));
        log.info("Site rebuild requested");
        return new PublicationStatus(true, now);
    }

    public PublicationStatus status() {
        return new PublicationStatus(trigger.isConfigured(),
                publications.findFirstByOrderByRequestedAtDesc().map(Publication::getRequestedAt).orElse(null));
    }
}
