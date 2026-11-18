package dev.elianhidalgo.portfolio.publishing.application;

import java.time.Instant;

/** Whether publishing can work, and when the site was last asked to rebuild (null: never). */
public record PublicationStatus(boolean configured, Instant lastRequestedAt) {}
