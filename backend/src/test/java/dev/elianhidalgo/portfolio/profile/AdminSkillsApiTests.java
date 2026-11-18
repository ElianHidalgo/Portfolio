package dev.elianhidalgo.portfolio.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.elianhidalgo.portfolio.TestTokens;
import dev.elianhidalgo.portfolio.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

/** The admin adds, edits, removes and reorders the skills; the public profile follows. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminSkillsApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PostgreSQLContainer postgres;

    private SingleConnectionDataSource ownerConnection;
    private JdbcTemplate owner;
    private ProfileSnapshot seeded;

    @BeforeEach
    void captureSeededSkills() {
        ownerConnection = new SingleConnectionDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true);
        owner = new JdbcTemplate(ownerConnection);
        seeded = new ProfileSnapshot(owner);
    }

    @AfterEach
    void restoreSeededSkills() {
        seeded.restore();
        ownerConnection.destroy();
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + TestTokens.valid());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(asAdmin(request).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private List<Long> orderedIds() {
        return owner.queryForList("select id from profile.skill order by display_order, name", Long.class);
    }

    private long idOf(String name) {
        return owner.queryForObject("select id from profile.skill where name = ?", Long.class, name);
    }

    @Test
    void requiresAdminToken() throws Exception {
        mockMvc.perform(post("/api/admin/skills").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kotlin\",\"category\":\"BACKEND\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsASkillAtTheEnd() throws Exception {
        int max = owner.queryForObject("select max(display_order) from profile.skill", Integer.class);

        send(post("/api/admin/skills"), "{\"name\":\"  Kotlin  \",\"category\":\"BACKEND\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Kotlin"))
                .andExpect(jsonPath("$.category").value("BACKEND"))
                .andExpect(jsonPath("$.displayOrder").value(max + 1));
    }

    @Test
    void updatesNameAndCategory() throws Exception {
        long id = idOf("Java");

        send(put("/api/admin/skills/" + id), "{\"name\":\"Java 21\",\"category\":\"TOOLS\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Java 21"))
                .andExpect(jsonPath("$.category").value("TOOLS"));

        mockMvc.perform(get("/api/profile"))
                .andExpect(jsonPath("$.skills[?(@.id == " + id + ")].name").value("Java 21"))
                .andExpect(jsonPath("$.skills[?(@.id == " + id + ")].category").value("TOOLS"));
    }

    @Test
    void changingCategoryPlacesTheSkillLastInItsNewGroup() throws Exception {
        long id = idOf("Java");
        int max = owner.queryForObject("select max(display_order) from profile.skill", Integer.class);

        send(put("/api/admin/skills/" + id), "{\"name\":\"Java\",\"category\":\"TOOLS\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayOrder").value(max + 1));

        assertThat(owner.queryForObject("select display_order from profile.skill where id = ?", Integer.class, id))
                .isEqualTo(max + 1);
        mockMvc.perform(get("/api/profile"))
                .andExpect(jsonPath("$.skills[?(@.category == 'TOOLS')].id").value(
                        org.hamcrest.Matchers.hasItem((int) id)))
                .andExpect(jsonPath("$.skills[-1:].id").value(org.hamcrest.Matchers.contains((int) id)));
    }

    @Test
    void keepingTheCategoryKeepsTheOrder() throws Exception {
        long id = idOf("Java");
        int before = owner.queryForObject("select display_order from profile.skill where id = ?", Integer.class, id);

        send(put("/api/admin/skills/" + id), "{\"name\":\"Java 21\",\"category\":\"BACKEND\"}")
                .andExpect(jsonPath("$.displayOrder").value(before));
    }

    @Test
    void duplicateSkillNameIs409() throws Exception {
        send(post("/api/admin/skills"), "{\"name\":\"JAVA\",\"category\":\"BACKEND\"}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        send(put("/api/admin/skills/" + idOf("Spring Boot")), "{\"name\":\"java\",\"category\":\"BACKEND\"}")
                .andExpect(status().isConflict());

        send(put("/api/admin/skills/" + idOf("Java")), "{\"name\":\"Java\",\"category\":\"TOOLS\"}")
                .andExpect(status().isOk());
    }

    @Test
    void rejectsUnknownCategory() throws Exception {
        send(post("/api/admin/skills"), "{\"name\":\"Cooking\",\"category\":\"COOKING\"}").andExpect(status().isBadRequest());
        send(post("/api/admin/skills"), "{\"name\":\"Cooking\"}").andExpect(status().isBadRequest());
        send(post("/api/admin/skills"), "{\"name\":\"  \",\"category\":\"TOOLS\"}").andExpect(status().isBadRequest());
        send(post("/api/admin/skills"), "{\"name\":\"" + "x".repeat(61) + "\",\"category\":\"TOOLS\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletesASkill() throws Exception {
        long id = idOf("Java");

        mockMvc.perform(asAdmin(delete("/api/admin/skills/" + id))).andExpect(status().isNoContent());

        assertThat(owner.queryForObject("select count(*) from profile.skill where id = ?", Integer.class, id)).isZero();
    }

    @Test
    void unknownSkillIs404() throws Exception {
        send(put("/api/admin/skills/999999"), "{\"name\":\"X\",\"category\":\"TOOLS\"}")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Skill not found"));
        mockMvc.perform(asAdmin(delete("/api/admin/skills/999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Skill not found"));
    }

    @Test
    void nonNumericIdIs400() throws Exception {
        mockMvc.perform(asAdmin(delete("/api/admin/skills/abc"))).andExpect(status().isBadRequest());
    }

    @Test
    void reordersAllSkills() throws Exception {
        List<Long> reversed = new ArrayList<>(orderedIds());
        Collections.reverse(reversed);

        send(put("/api/admin/skills/order"), "{\"ids\":" + reversed + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(reversed.size()))
                .andExpect(jsonPath("$[0].id").value(reversed.get(0)))
                .andExpect(jsonPath("$[0].displayOrder").value(0))
                .andExpect(jsonPath("$[1].displayOrder").value(1));

        mockMvc.perform(get("/api/profile"))
                .andExpect(jsonPath("$.skills[0].id").value(reversed.get(0)))
                .andExpect(jsonPath("$.skills[19].id").value(reversed.get(19)));
    }

    @Test
    void reorderRejectsAPartialList() throws Exception {
        List<Long> ids = orderedIds();
        List<Long> missing = ids.subList(1, ids.size());
        List<Long> duplicated = new ArrayList<>(ids);
        duplicated.set(1, ids.get(0));
        List<Long> unknown = new ArrayList<>(ids);
        unknown.set(1, 999_999L);

        for (List<Long> attempt : List.of(missing, duplicated, unknown)) {
            send(put("/api/admin/skills/order"), "{\"ids\":" + attempt + "}").andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        send(put("/api/admin/skills/order"), "{}").andExpect(status().isBadRequest());
        assertThat(orderedIds()).isEqualTo(ids);
    }
}
