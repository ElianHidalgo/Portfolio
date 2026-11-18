package dev.elianhidalgo.portfolio.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.elianhidalgo.portfolio.TestTokens;
import dev.elianhidalgo.portfolio.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The admin's inbox: list, mark read or unread, delete — only with a valid admin token. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminMessagesApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PostgreSQLContainer postgres;

    private JdbcTemplate owner;

    @BeforeEach
    void threeMessages() {
        owner = new JdbcTemplate(new SingleConnectionDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true));
        owner.update("delete from contact.message");
        insert("Old", "old@example.com", "The oldest message.", "es", true, "2026-11-01T10:00:00Z");
        insert("Mid", "mid@example.com", "A message in the middle.", "en", false, "2026-11-02T10:00:00Z");
        insert("New", "new@example.com", "Line one\nline two.", "es", false, "2026-11-03T10:00:00Z");
    }

    private void insert(String name, String email, String body, String locale, boolean read, String createdAt) {
        owner.update("insert into contact.message(name, email, body, locale, read, created_at) values (?, ?, ?, ?, ?, ?::timestamptz)",
                name, email, body, locale, read, createdAt);
    }

    private long idOf(String name) {
        return owner.queryForObject("select id from contact.message where name = ?", Long.class, name);
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + TestTokens.valid());
    }

    @Test
    void requiresAdminToken() throws Exception {
        mockMvc.perform(get("/api/admin/messages")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsNewestFirstWithCounts() throws Exception {
        mockMvc.perform(asAdmin(get("/api/admin/messages")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.unread").value(2))
                .andExpect(jsonPath("$.items[0].name").value("New"))
                .andExpect(jsonPath("$.items[0].email").value("new@example.com"))
                .andExpect(jsonPath("$.items[0].message").value("Line one\nline two."))
                .andExpect(jsonPath("$.items[0].locale").value("es"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andExpect(jsonPath("$.items[2].name").value("Old"));
    }

    @Test
    void pagesWithACursorAndClampsSize() throws Exception {
        mockMvc.perform(asAdmin(get("/api/admin/messages").param("size", "500")))
                .andExpect(jsonPath("$.items.length()", lessThanOrEqualTo(50)));
        // A cursor ("older than this id") stays correct when messages are deleted or arrive meanwhile.
        mockMvc.perform(asAdmin(get("/api/admin/messages").param("before", String.valueOf(idOf("Mid"))).param("size", "2")))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Old"))
                .andExpect(jsonPath("$.total").value(3));
        owner.update("delete from contact.message where name = 'Old'");
        mockMvc.perform(asAdmin(get("/api/admin/messages").param("before", String.valueOf(idOf("Mid")))))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void rejectsAMissingOrInvalidReadFlag() throws Exception {
        for (String body : new String[] {"{}", "{\"read\":\"x\"}"}) {
            mockMvc.perform(asAdmin(patch("/api/admin/messages/" + idOf("New") + "/read"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void marksReadAndUnread() throws Exception {
        long id = idOf("New");
        mockMvc.perform(asAdmin(patch("/api/admin/messages/" + id + "/read"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"read\":true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(asAdmin(get("/api/admin/messages"))).andExpect(jsonPath("$.unread").value(1));

        mockMvc.perform(asAdmin(patch("/api/admin/messages/" + id + "/read"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"read\":false}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(asAdmin(get("/api/admin/messages"))).andExpect(jsonPath("$.unread").value(2));
    }

    @Test
    void deletesAMessage() throws Exception {
        mockMvc.perform(asAdmin(delete("/api/admin/messages/" + idOf("Mid")))).andExpect(status().isNoContent());

        assertThat(owner.queryForObject("select count(*) from contact.message", Integer.class)).isEqualTo(2);
    }

    @Test
    void unknownMessageIs404() throws Exception {
        mockMvc.perform(asAdmin(patch("/api/admin/messages/999999/read"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"read\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Message not found"));
        mockMvc.perform(asAdmin(delete("/api/admin/messages/999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Message not found"));
    }

    @Test
    void nonNumericIdIs400() throws Exception {
        mockMvc.perform(asAdmin(delete("/api/admin/messages/abc")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void corsAllowsPatchAndDeleteFromTheSite() throws Exception {
        for (String method : new String[] {"PATCH", "DELETE"}) {
            mockMvc.perform(options("/api/admin/messages/1").header("Origin", "http://localhost:4200")
                            .header("Access-Control-Request-Method", method)
                            .header("Access-Control-Request-Headers", "authorization,content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        }
    }
}
