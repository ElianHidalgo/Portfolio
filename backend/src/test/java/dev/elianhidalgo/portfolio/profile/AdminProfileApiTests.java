package dev.elianhidalgo.portfolio.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.elianhidalgo.portfolio.TestTokens;
import dev.elianhidalgo.portfolio.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The admin edits the single owner profile; the public API reflects it. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminProfileApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PostgreSQLContainer postgres;

    private JdbcTemplate owner;
    private ProfileSnapshot seeded;

    @BeforeEach
    void captureSeededProfile() {
        owner = new JdbcTemplate(new SingleConnectionDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true));
        seeded = new ProfileSnapshot(owner);
    }

    @AfterEach
    void restoreSeededProfile() {
        seeded.restore();
    }

    private static final String VALID = """
            {"fullName":"Ana","headline":{"es":"Hola","en":"Hello"},"about":{"es":"Sobre mi","en":"About me"},
             "location":"Quito","email":"ana@example.com","githubUrl":"https://github.com/ana",
             "linkedinUrl":"https://www.linkedin.com/in/ana","avatarUrl":"avatar.png",
             "cvUrl":{"es":"cv/cv-es.pdf","en":"cv/cv-en.pdf"}}""";

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + TestTokens.valid());
    }

    private ResultActions putProfile(String body) throws Exception {
        return mockMvc.perform(asAdmin(put("/api/admin/profile")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void requiresAdminToken() throws Exception {
        mockMvc.perform(get("/api/admin/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/profile").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readsTheCurrentProfile() throws Exception {
        mockMvc.perform(asAdmin(get("/api/admin/profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Victor Hidalgo"))
                .andExpect(jsonPath("$.skills[0].id").exists())
                .andExpect(jsonPath("$.skills[0].name").exists());
    }

    @Test
    void updatesEveryFieldAndTrims() throws Exception {
        OffsetDateTime before = owner.queryForObject("select updated_at from profile.profile", OffsetDateTime.class);

        putProfile(VALID.replace("\"Ana\"", "\"  Ana  \"").replace("\"Hola\"", "\"  Hola  \""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana"))
                .andExpect(jsonPath("$.headline.es").value("Hola"))
                .andExpect(jsonPath("$.headline.en").value("Hello"))
                .andExpect(jsonPath("$.about.es").value("Sobre mi"))
                .andExpect(jsonPath("$.location").value("Quito"))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.githubUrl").value("https://github.com/ana"))
                .andExpect(jsonPath("$.linkedinUrl").value("https://www.linkedin.com/in/ana"))
                .andExpect(jsonPath("$.avatarUrl").value("avatar.png"))
                .andExpect(jsonPath("$.cvUrl.en").value("cv/cv-en.pdf"))
                .andExpect(jsonPath("$.skills.length()").value(20));

        mockMvc.perform(get("/api/profile"))
                .andExpect(jsonPath("$.fullName").value("Ana"))
                .andExpect(jsonPath("$.about.en").value("About me"));
        OffsetDateTime after = owner.queryForObject("select updated_at from profile.profile", OffsetDateTime.class);
        assertThat(after).isAfter(before);
    }

    @Test
    void blankOptionalFieldsBecomeNull() throws Exception {
        putProfile(VALID.replace("\"Quito\"", "\"  \"").replace("\"avatar.png\"", "\"\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value(nullValue()))
                .andExpect(jsonPath("$.avatarUrl").value(nullValue()));
    }

    @Test
    void rejectsMissingOrTooLongText() throws Exception {
        putProfile(VALID.replace("\"Hola\"", "\"\""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("headline.es")));
        putProfile(VALID.replace("\"About me\"", "\"" + "x".repeat(2501) + "\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("about.en")));
        putProfile(VALID.replace("\"Hola\"", "\"" + "x".repeat(161) + "\""))
                .andExpect(status().isBadRequest());
        putProfile("{}").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnsafeLinksAndPaths() throws Exception {
        putProfile(VALID.replace("https://github.com/ana", "javascript:alert(1)")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("githubUrl")));
        putProfile(VALID.replace("\"avatar.png\"", "\"//evil.example/a.png\"")).andExpect(status().isBadRequest());
        putProfile(VALID.replace("cv/cv-es.pdf", "../x.pdf")).andExpect(status().isBadRequest());
        putProfile(VALID.replace("https://www.linkedin.com/in/ana", "http://www.linkedin.com/in/ana"))
                .andExpect(status().isBadRequest());
        putProfile(VALID.replace("\"avatar.png\"", "\"javascript:alert(1)\"")).andExpect(status().isBadRequest());
    }

    @Test
    void acceptsRelativePathsAndHttps() throws Exception {
        putProfile(VALID.replace("\"avatar.png\"", "\"https://example.com/a.jpg\"")).andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("https://example.com/a.jpg"))
                .andExpect(jsonPath("$.cvUrl.es").value("cv/cv-es.pdf"));
    }
}
