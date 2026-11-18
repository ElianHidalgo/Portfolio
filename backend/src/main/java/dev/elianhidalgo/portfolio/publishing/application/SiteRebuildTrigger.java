package dev.elianhidalgo.portfolio.publishing.application;

import java.util.Optional;

/**
 * Starts a rebuild of the public site (port). The Cloudflare adapter implements it; tests
 * replace it, so no test ever calls Cloudflare.
 */
public interface SiteRebuildTrigger {

    /** Whether a rebuild can be requested at all (the hook URL is configured). */
    boolean isConfigured();

    /**
     * Requests a rebuild and returns the build id when the provider gives one.
     *
     * @throws PublishingNotConfiguredException when no hook is configured
     * @throws RebuildFailedException when the provider refuses or cannot be reached
     */
    Optional<String> trigger();
}
