package dev.elianhidalgo.portfolio.publishing.application;

/** No deploy hook is configured (CLOUDFLARE_DEPLOY_HOOK_URL is empty). */
public class PublishingNotConfiguredException extends RuntimeException {

    public PublishingNotConfiguredException() {
        super("Publishing is not configured");
    }
}
