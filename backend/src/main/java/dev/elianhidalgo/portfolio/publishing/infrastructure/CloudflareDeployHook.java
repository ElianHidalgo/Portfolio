package dev.elianhidalgo.portfolio.publishing.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.elianhidalgo.portfolio.publishing.application.RebuildFailedException;
import dev.elianhidalgo.portfolio.publishing.application.SiteRebuildTrigger;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Rebuilds the site through a Cloudflare Workers Builds Deploy Hook: a POST to a secret URL.
 * The URL is a credential, so it is never logged nor put in error messages.
 */
public class CloudflareDeployHook implements SiteRebuildTrigger {

    private static final Logger log = LoggerFactory.getLogger(CloudflareDeployHook.class);

    record HookResponse(Result result) {
        record Result(@JsonProperty("build_uuid") String buildUuid) {}
    }

    private final RestClient client;
    private final String hookUrl;

    public CloudflareDeployHook(RestClient.Builder builder, String hookUrl) {
        this.client = builder.build();
        this.hookUrl = hookUrl;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public Optional<String> trigger() {
        try {
            HookResponse response = client.post().uri(hookUrl).retrieve().body(HookResponse.class);
            return Optional.ofNullable(response).map(HookResponse::result).map(HookResponse.Result::buildUuid);
        } catch (RestClientResponseException refused) {
            log.warn("Site rebuild failed: Cloudflare answered {}", refused.getStatusCode().value());
            throw new RebuildFailedException("Cloudflare answered " + refused.getStatusCode().value());
        } catch (RestClientException unreachable) {
            log.warn("Site rebuild failed: Cloudflare could not be reached");
            throw new RebuildFailedException("Cloudflare could not be reached");
        }
    }
}
