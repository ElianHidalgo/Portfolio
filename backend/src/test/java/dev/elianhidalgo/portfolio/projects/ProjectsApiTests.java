package dev.elianhidalgo.portfolio.projects;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.elianhidalgo.portfolio.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ProjectsApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void listsPublishedProjectsInDisplayOrder() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug").value(contains("portfolio", "gamevault", "proxy-cache-api")))
                .andExpect(jsonPath("$[0].title.en").value("Personal portfolio"))
                .andExpect(jsonPath("$[0].technologies").value(hasItem("Spring Boot")))
                .andExpect(jsonPath("$[0].repoUrl").value("https://github.com/ElianHidalgo/Portfolio"))
                .andExpect(jsonPath("$[0].featured").value(true))
                .andExpect(jsonPath("$[0].description").doesNotExist());
    }

    @Test
    void breaksDisplayOrderTiesBySlug() throws Exception {
        jdbc.update("insert into project (slug, title_es, title_en, summary_es, summary_en, description_es, description_en, display_order) "
                + "values ('aaa', 'A', 'A', 'a', 'a', 'a', 'a', 1)");

        mockMvc.perform(get("/api/projects")).andExpect(jsonPath("$[0].slug").value("aaa"));
    }

    @Test
    void hidesUnpublishedProjects() throws Exception {
        jdbc.update("update project set published = false where slug = 'gamevault'");

        mockMvc.perform(get("/api/projects")).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/projects/gamevault")).andExpect(status().isNotFound());
    }

    @Test
    void returnsProjectDetailBySlug() throws Exception {
        mockMvc.perform(get("/api/projects/gamevault"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title.es").value("GameVault"))
                .andExpect(jsonPath("$.repoUrl").value("https://github.com/ElianHidalgo/GameVault"))
                .andExpect(jsonPath("$.description.en").value(startsWith("GameVault is a cross-platform")));
    }

    @Test
    void returnsNotFoundProblemForUnknownSlug() throws Exception {
        mockMvc.perform(get("/api/projects/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Project not found"));
    }
}
