package dev.elianhidalgo.portfolio.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.elianhidalgo.portfolio.publishing.application.RebuildFailedException;
import dev.elianhidalgo.portfolio.publishing.infrastructure.CloudflareDeployHook;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The Cloudflare adapter, against a simulated server: no network, no real hook. */
class CloudflareDeployHookTests {

    private static final String HOOK = "https://api.cloudflare.com/client/v4/workers/builds/deploy_hooks/secret-hook-id";

    private MockRestServiceServer server;
    private CloudflareDeployHook hook;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        hook = new CloudflareDeployHook(builder, HOOK);
    }

    @Test
    void postsToTheHookAndReadsTheBuildId() {
        server.expect(requestTo(HOOK)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\":true,\"result\":{\"build_uuid\":\"abc\"}}", MediaType.APPLICATION_JSON));

        assertThat(hook.trigger()).contains("abc");
        assertThat(hook.isConfigured()).isTrue();
        server.verify();
    }

    @Test
    void cloudflareErrorBecomesRebuildFailed() {
        server.expect(requestTo(HOOK)).andRespond(withServerError());

        assertThatThrownBy(hook::trigger)
                .isInstanceOf(RebuildFailedException.class)
                .message().doesNotContain("secret-hook-id");
    }

    @Test
    void cloudflareTimeoutIsReported() {
        server.expect(requestTo(HOOK)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(hook::trigger)
                .isInstanceOf(RebuildFailedException.class)
                .message().doesNotContain("secret-hook-id");
    }
}
