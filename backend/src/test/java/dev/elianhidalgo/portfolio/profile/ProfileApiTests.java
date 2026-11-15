package dev.elianhidalgo.portfolio.profile;

import static org.hamcrest.Matchers.contains;
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
class ProfileApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void returnsSeededProfileInBothLanguages() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Elian Hidalgo"))
                .andExpect(jsonPath("$.headline.en").value("Full-stack developer — Java, Spring & Angular"))
                .andExpect(jsonPath("$.headline.es").value("Desarrollador full-stack — Java, Spring y Angular"))
                .andExpect(jsonPath("$.githubUrl").value("https://github.com/ElianHidalgo"))
                .andExpect(jsonPath("$.location").isEmpty())
                .andExpect(jsonPath("$.cvUrl.es").isEmpty());
    }

    @Test
    void returnsSkillsOrderedByDisplayOrderThenName() throws Exception {
        jdbc.update("insert into skill (name, category, display_order) values ('Kotlin', 'BACKEND', 1)");

        mockMvc.perform(get("/api/profile"))
                .andExpect(jsonPath("$.skills[*].name").value(contains(
                        "Java", "Kotlin", "Spring Boot", "Angular", "TypeScript", "PostgreSQL", "Docker", "Git")))
                .andExpect(jsonPath("$.skills[0].category").value("BACKEND"));
    }

    @Test
    void returnsNotFoundProblemWhenProfileIsMissing() throws Exception {
        jdbc.update("delete from profile");

        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Profile has not been created yet"));
    }
}
