package dev.elianhidalgo.portfolio.publishing.infrastructure;

import dev.elianhidalgo.portfolio.publishing.application.PublishingNotConfiguredException;
import dev.elianhidalgo.portfolio.publishing.application.SiteRebuildTrigger;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Chooses the rebuild trigger: the Cloudflare hook when CLOUDFLARE_DEPLOY_HOOK_URL is set. */
@Configuration
class PublishingConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    @Bean
    SiteRebuildTrigger siteRebuildTrigger(@Value("${app.publishing.deploy-hook-url:}") String hookUrl) {
        if (hookUrl == null || hookUrl.isBlank()) {
            return new NotConfigured();
        }
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return new CloudflareDeployHook(RestClient.builder().requestFactory(requestFactory), hookUrl.strip());
    }

    /** Local development, or the Render variable is missing: publishing answers 503. */
    static final class NotConfigured implements SiteRebuildTrigger {

        @Override
        public boolean isConfigured() {
            return false;
        }

        @Override
        public Optional<String> trigger() {
            throw new PublishingNotConfiguredException();
        }
    }
}
