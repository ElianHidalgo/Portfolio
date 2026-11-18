package dev.elianhidalgo.portfolio.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.elianhidalgo.portfolio.TestTokens;
import dev.elianhidalgo.portfolio.TestcontainersConfiguration;
import dev.elianhidalgo.portfolio.publishing.application.PublishingNotConfiguredException;
import dev.elianhidalgo.portfolio.publishing.application.RebuildFailedException;
import dev.elianhidalgo.portfolio.publishing.application.SiteRebuildTrigger;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** "Publicar": rebuilds the site through the trigger (never Cloudflare in tests) and records it. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PublishApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PostgreSQLContainer postgres;

    @MockitoBean
    SiteRebuildTrigger trigger;

    private JdbcTemplate owner;

    @BeforeEach
    void noPublications() {
        owner = new JdbcTemplate(new SingleConnectionDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true));
        owner.update("delete from publishing.publication");
        when(trigger.isConfigured()).thenReturn(true);
    }

    private ResultActions publish() throws Exception {
        return mockMvc.perform(post("/api/admin/publish").header("Authorization", "Bearer " + TestTokens.valid()));
    }

    private ResultActions publishStatus() throws Exception {
        return mockMvc.perform(get("/api/admin/publish/status").header("Authorization", "Bearer " + TestTokens.valid()));
    }

    private int publications() {
        return owner.queryForObject("select count(*) from publishing.publication", Integer.class);
    }

    @Test
    void publishRequiresAdminToken() throws Exception {
        mockMvc.perform(post("/api/admin/publish")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/publish/status")).andExpect(status().isUnauthorized());
    }

    @Test
    void publishTriggersARebuildAndRecordsIt() throws Exception {
        when(trigger.trigger()).thenReturn(Optional.of("b-1"));
        publishStatus().andExpect(jsonPath("$.configured").value(true)).andExpect(jsonPath("$.lastRequestedAt").isEmpty());

        publish()
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.lastRequestedAt").isNotEmpty());

        assertThat(owner.queryForObject("select build_id from publishing.publication", String.class)).isEqualTo("b-1");
        publishStatus().andExpect(jsonPath("$.lastRequestedAt").isNotEmpty());
    }

    @Test
    void secondPublishWithinAMinuteIs429() throws Exception {
        when(trigger.trigger()).thenReturn(Optional.empty());

        publish().andExpect(status().isAccepted());
        publish()
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("A publication is already in progress"));

        verify(trigger, times(1)).trigger();
        assertThat(publications()).isEqualTo(1);
    }

    @Test
    void cloudflareFailureIsReportedAndNotRecorded() throws Exception {
        doThrow(new RebuildFailedException("Cloudflare answered 500")).when(trigger).trigger();

        publish()
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The site could not be rebuilt"));

        assertThat(publications()).isZero();
    }

    @Test
    void unconfiguredHookIs503() throws Exception {
        when(trigger.isConfigured()).thenReturn(false);
        doThrow(new PublishingNotConfiguredException()).when(trigger).trigger();

        publish()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Publishing is not configured"));
        publishStatus().andExpect(jsonPath("$.configured").value(false));
        assertThat(publications()).isZero();
    }
}
