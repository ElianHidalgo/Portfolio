package dev.elianhidalgo.portfolio.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.elianhidalgo.portfolio.TestcontainersConfiguration;
import java.util.Map;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"app.contact.per-client-per-hour=2", "app.contact.daily-cap=3"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ContactApiTests {

    private static final String VALID = """
            {"name":"Ana","email":"ana@example.com","message":"Hello, I have a project.","locale":"es","website":""}""";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PostgreSQLContainer postgres;

    private JdbcTemplate owner;

    @BeforeEach
    void emptyInbox() {
        owner = new JdbcTemplate(new SingleConnectionDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true));
        owner.update("delete from contact.message");
    }

    /** A request as it reaches Render: Cloudflare sets CF-Connecting-IP to the real client. */
    private ResultActions send(String clientIp, String json) throws Exception {
        return mockMvc.perform(post("/api/contact").header("CF-Connecting-IP", clientIp)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private int storedMessages() {
        return owner.queryForObject("select count(*) from contact.message", Integer.class);
    }

    @Test
    void storesAValidMessage() throws Exception {
        send("203.0.113.10", VALID).andExpect(status().isAccepted());

        Map<String, Object> row = owner.queryForMap("select name, email, body, locale, read from contact.message");
        assertThat(row).containsEntry("name", "Ana").containsEntry("email", "ana@example.com")
                .containsEntry("body", "Hello, I have a project.").containsEntry("locale", "es")
                .containsEntry("read", false);
    }

    @Test
    void storesTrimmedFieldsAndKeepsLineBreaks() throws Exception {
        send("203.0.113.11", """
                {"name":"  Ana ","email":" ana@example.com ","message":"Line one\\nline two.  ","locale":"en"}""")
                .andExpect(status().isAccepted());

        Map<String, Object> row = owner.queryForMap("select name, email, body from contact.message");
        assertThat(row).containsEntry("name", "Ana").containsEntry("email", "ana@example.com")
                .containsEntry("body", "Line one\nline two.");
    }

    @Test
    void honeypotIsAcceptedButNotStored() throws Exception {
        send("203.0.113.12", VALID.replace("\"website\":\"\"", "\"website\":\"https://spam.example\""))
                .andExpect(status().isAccepted());

        assertThat(storedMessages()).isZero();
    }

    @Test
    void rejectsInvalidFields() throws Exception {
        send("203.0.113.13", """
                {"name":"","email":"nope","message":"short","locale":"fr"}""")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(storedMessages()).isZero();
    }

    @Test
    void rejectsControlCharacters() throws Exception {
        send("203.0.113.14", VALID.replace("\"Ana\"", "\"A\\u0000na\"")).andExpect(status().isBadRequest());
        send("203.0.113.14", VALID.replace("I have", "I\\u0007have")).andExpect(status().isBadRequest());

        assertThat(storedMessages()).isZero();
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        send("203.0.113.15", "{")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(post("/api/contact").header("CF-Connecting-IP", "203.0.113.15")
                        .contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void limitsMessagesPerClient() throws Exception {
        send("203.0.113.16", VALID).andExpect(status().isAccepted());
        send("203.0.113.16", VALID).andExpect(status().isAccepted());
        send("203.0.113.16", VALID)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value("Too many messages, try again later"));

        send("203.0.113.17", VALID).andExpect(status().isAccepted());
    }

    @Test
    void capsStoredMessagesPerDay() throws Exception {
        send("203.0.113.18", VALID).andExpect(status().isAccepted());
        send("203.0.113.19", VALID).andExpect(status().isAccepted());
        send("203.0.113.20", VALID).andExpect(status().isAccepted());
        send("203.0.113.21", VALID)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The inbox is full for today"));

        assertThat(storedMessages()).isEqualTo(3);
    }

    @Test
    void responseIsNeverCached() throws Exception {
        send("203.0.113.22", VALID)
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
