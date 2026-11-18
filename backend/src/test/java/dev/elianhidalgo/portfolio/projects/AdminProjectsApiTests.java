package dev.elianhidalgo.portfolio.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

/** The admin creates, edits, hides, reorders and deletes projects; the public list follows. */
@SpringBootTest(properties = "app.rate-limit.requests-per-minute=100000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminProjectsApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PostgreSQLContainer postgres;

    private SingleConnectionDataSource ownerConnection;
    private JdbcTemplate owner;
    private List<Map<String, Object>> seeded;

    @BeforeEach
    void captureSeededProjects() {
        ownerConnection = new SingleConnectionDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true);
        owner = new JdbcTemplate(ownerConnection);
        seeded = owner.queryForList("select *, technologies::text as tech_text from projects.project order by id");
    }

    @AfterEach
    void restoreSeededProjects() {
        owner.update("delete from projects.project");
        for (var p : seeded) {
            owner.update("""
                    insert into projects.project (id, slug, title_es, title_en, summary_es, summary_en, description_es,
                        description_en, technologies, repo_url, demo_url, image_url, featured, display_order, published,
                        created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?::varchar[], ?, ?, ?, ?, ?, ?, ?)""",
                    p.get("id"), p.get("slug"), p.get("title_es"), p.get("title_en"), p.get("summary_es"),
                    p.get("summary_en"), p.get("description_es"), p.get("description_en"), p.get("tech_text"),
                    p.get("repo_url"), p.get("demo_url"), p.get("image_url"), p.get("featured"),
                    p.get("display_order"), p.get("published"), p.get("created_at"));
        }
        ownerConnection.destroy();
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + TestTokens.valid());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(asAdmin(request).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(asAdmin(request));
    }

    /** A valid body; each override replaces the raw JSON of one property. */
    private static String body(Map<String, String> overrides) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("slug", "\"new-thing\"");
        fields.put("title", "{\"es\":\"Cosa nueva\",\"en\":\"New thing\"}");
        fields.put("summary", "{\"es\":\"Resumen\",\"en\":\"Summary\"}");
        fields.put("description", "{\"es\":\"Descripcion\",\"en\":\"Description\"}");
        fields.put("technologies", "[\"Java\",\"Spring Boot\"]");
        fields.put("repoUrl", "\"https://github.com/x/y\"");
        fields.put("demoUrl", "null");
        fields.put("featured", "false");
        fields.put("published", "false");
        fields.putAll(overrides);
        return fields.entrySet().stream().map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String body(String key, String value) {
        return body(Map.of(key, value));
    }

    private static String text(String es, String en) {
        return "{\"es\":\"" + es + "\",\"en\":\"" + en + "\"}";
    }

    private static String twentyOneTechnologies() {
        var items = new ArrayList<String>();
        for (int i = 0; i < 21; i++) {
            items.add("\"t" + i + "\"");
        }
        return items.stream().collect(Collectors.joining(",", "[", "]"));
    }

    private long idOf(String slug) {
        return owner.queryForObject("select id from projects.project where slug = ?", Long.class, slug);
    }

    private List<Long> orderedIds() {
        return owner.queryForList("select id from projects.project order by display_order, slug", Long.class);
    }

    @Test
    void requiresAdminToken() throws Exception {
        mockMvc.perform(get("/api/admin/projects")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/projects").contentType(MediaType.APPLICATION_JSON).content(body(Map.of())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/projects/1").contentType(MediaType.APPLICATION_JSON).content(body(Map.of())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/projects/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/projects/order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsAllProjectsIncludingHidden() throws Exception {
        send(put("/api/admin/projects/" + idOf("gamevault")), body(Map.of("published", "false")))
                .andExpect(status().isOk());

        call(get("/api/admin/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].slug").value(contains("portfolio", "gamevault", "proxy-cache-api")));
        mockMvc.perform(get("/api/projects")).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void createsAHiddenProjectAtTheEnd() throws Exception {
        int max = owner.queryForObject("select max(display_order) from projects.project", Integer.class);

        send(post("/api/admin/projects"), body(Map.of()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.slug").value("new-thing"))
                .andExpect(jsonPath("$.title.en").value("New thing"))
                .andExpect(jsonPath("$.technologies").value(contains("Java", "Spring Boot")))
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.displayOrder").value(max + 1));

        mockMvc.perform(get("/api/projects")).andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/api/projects/new-thing")).andExpect(status().isNotFound());
    }

    @Test
    void duplicateSlugIs409() throws Exception {
        send(post("/api/admin/projects"), body("slug", "\"portfolio\""))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("A project with that slug already exists"));
    }

    @Test
    void rejectsBadSlugs() throws Exception {
        for (String slug : List.of("New Thing", "-a", "a--b", "a/b", "a".repeat(81))) {
            send(post("/api/admin/projects"), body("slug", "\"" + slug + "\""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("slug")));
        }
        assertThat(owner.queryForObject("select count(*) from projects.project", Integer.class)).isEqualTo(3);
    }

    @Test
    void updatesEveryEditableField() throws Exception {
        long id = idOf("gamevault");

        send(put("/api/admin/projects/" + id), body(Map.of(
                        "title", text("Titulo", "Title"),
                        "summary", text("Resumen nuevo", "New summary"),
                        "description", text("Desc es", "Desc en"),
                        "technologies", "[\"Zig\",\"Angular\",\"Java\"]",
                        "repoUrl", "\"https://github.com/x/z\"",
                        "demoUrl", "\"https://demo.example/app\"",
                        "featured", "true",
                        "published", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.slug").value("gamevault"));

        mockMvc.perform(get("/api/projects/gamevault"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title.es").value("Titulo"))
                .andExpect(jsonPath("$.title.en").value("Title"))
                .andExpect(jsonPath("$.summary.en").value("New summary"))
                .andExpect(jsonPath("$.description.es").value("Desc es"))
                .andExpect(jsonPath("$.technologies").value(contains("Zig", "Angular", "Java")))
                .andExpect(jsonPath("$.demoUrl").value("https://demo.example/app"))
                .andExpect(jsonPath("$.featured").value(true));
    }

    @Test
    void updateIgnoresSlugAndImage() throws Exception {
        long id = idOf("gamevault");
        String imageBefore = owner.queryForObject("select image_url from projects.project where id = ?",
                String.class, id);

        send(put("/api/admin/projects/" + id), body(Map.of(
                        "slug", "\"other\"", "imageUrl", "\"javascript:x\"", "published", "true")))
                .andExpect(status().isOk());

        assertThat(owner.queryForObject("select slug from projects.project where id = ?", String.class, id))
                .isEqualTo("gamevault");
        assertThat(owner.queryForObject("select image_url from projects.project where id = ?", String.class, id))
                .isEqualTo(imageBefore);
    }

    @Test
    void blankLinksBecomeNull() throws Exception {
        send(put("/api/admin/projects/" + idOf("portfolio")), body(Map.of(
                        "repoUrl", "\"  \"", "demoUrl", "\"\"", "published", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repoUrl").doesNotExist());

        assertThat(owner.queryForObject("select repo_url from projects.project where slug = 'portfolio'",
                String.class)).isNull();
        assertThat(owner.queryForObject("select demo_url from projects.project where slug = 'portfolio'",
                String.class)).isNull();
    }

    @Test
    void rejectsUnsafeLinks() throws Exception {
        long id = idOf("portfolio");
        for (String url : List.of("javascript:alert(1)", "http://x", "//evil.example")) {
            send(put("/api/admin/projects/" + id), body("repoUrl", "\"" + url + "\""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("repoUrl")));
            send(put("/api/admin/projects/" + id), body("demoUrl", "\"" + url + "\""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("demoUrl")));
        }
    }

    @Test
    void rejectsMissingOrTooLongText() throws Exception {
        long id = idOf("portfolio");
        send(put("/api/admin/projects/" + id), body("title", text("", "T"))).andExpect(status().isBadRequest());
        send(put("/api/admin/projects/" + id), body("summary", text("s", "x".repeat(301))))
                .andExpect(status().isBadRequest());
        send(put("/api/admin/projects/" + id), body("description", text("x".repeat(4001), "d")))
                .andExpect(status().isBadRequest());
        send(put("/api/admin/projects/" + id), body("title", "{\"es\":\"T\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void acceptsMaximumLengthDescriptions() throws Exception {
        String max = "ñ".repeat(4000);
        send(put("/api/admin/projects/" + idOf("portfolio")), body(Map.of(
                        "description", text(max, max), "published", "true")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsBadTechnologies() throws Exception {
        long id = idOf("portfolio");
        for (String technologies : List.of("[\"Java\",\"java\"]", "[\"Java\",\" \"]", twentyOneTechnologies(),
                "[\"" + "x".repeat(41) + "\"]", "null")) {
            send(put("/api/admin/projects/" + id), body("technologies", technologies))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("technologies")));
        }
    }

    @Test
    void rejectsMissingFlags() throws Exception {
        long id = idOf("portfolio");
        String noPublished = body(Map.of()).replace(",\"published\":false", "");
        String noFeatured = body(Map.of()).replace(",\"featured\":false", "");
        assertThat(noPublished).doesNotContain("published");

        send(put("/api/admin/projects/" + id), noPublished)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("published")));
        send(put("/api/admin/projects/" + id), body("published", "null")).andExpect(status().isBadRequest());
        send(post("/api/admin/projects"), noFeatured)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("featured")));

        assertThat(owner.queryForObject("select published from projects.project where id = ?", Boolean.class, id))
                .isTrue();
        assertThat(owner.queryForObject("select count(*) from projects.project", Integer.class)).isEqualTo(3);
    }

    @Test
    void keepsAtLeastOnePublishedProject() throws Exception {
        long portfolio = idOf("portfolio");
        long gamevault = idOf("gamevault");
        long proxy = idOf("proxy-cache-api");
        String hide = body(Map.of("published", "false"));

        send(put("/api/admin/projects/" + gamevault), hide).andExpect(status().isOk());
        send(put("/api/admin/projects/" + proxy), hide).andExpect(status().isOk());

        send(put("/api/admin/projects/" + portfolio), hide)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("At least one project must stay published"));
        call(delete("/api/admin/projects/" + portfolio))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("At least one project must stay published"));
        assertThat(owner.queryForObject("select published from projects.project where id = ?", Boolean.class,
                portfolio)).isTrue();

        call(delete("/api/admin/projects/" + gamevault)).andExpect(status().isNoContent());
    }

    @Test
    void deletesAProject() throws Exception {
        long id = idOf("gamevault");

        call(delete("/api/admin/projects/" + id)).andExpect(status().isNoContent());

        call(get("/api/admin/projects/" + id)).andExpect(status().isNotFound());
        assertThat(orderedIds()).doesNotContain(id);
    }

    @Test
    void unknownProjectIs404() throws Exception {
        call(get("/api/admin/projects/999999"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Project not found"));
        send(put("/api/admin/projects/999999"), body(Map.of()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Project not found"));
        call(delete("/api/admin/projects/999999"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Project not found"));
        call(get("/api/admin/projects/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void reordersAllProjects() throws Exception {
        List<Long> reversed = new ArrayList<>(orderedIds());
        Collections.reverse(reversed);

        send(put("/api/admin/projects/order"), "{\"ids\":" + reversed + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug").value(contains("proxy-cache-api", "gamevault", "portfolio")))
                .andExpect(jsonPath("$[0].displayOrder").value(0));

        assertThat(orderedIds()).isEqualTo(reversed);
        mockMvc.perform(get("/api/projects"))
                .andExpect(jsonPath("$[*].slug").value(contains("proxy-cache-api", "gamevault", "portfolio")));
    }

    @Test
    void reorderRejectsAPartialList() throws Exception {
        List<Long> before = orderedIds();
        long a = before.get(0);
        long b = before.get(1);
        long c = before.get(2);

        for (String ids : List.of("[" + a + "," + b + "]", "[" + a + "," + a + "," + b + "]",
                "[" + a + "," + b + ",999999]", "[" + a + "," + b + "," + c + "," + a + "]")) {
            send(put("/api/admin/projects/order"), "{\"ids\":" + ids + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("The order must list every project exactly once"));
        }
        assertThat(orderedIds()).isEqualTo(before);
    }
}
