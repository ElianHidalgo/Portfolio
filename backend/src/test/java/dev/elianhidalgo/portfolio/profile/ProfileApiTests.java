package dev.elianhidalgo.portfolio.profile;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
    void returnsOwnerProfileInBothLanguages() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Victor Hidalgo"))
                .andExpect(jsonPath("$.headline.en").value("Software Engineering student focused on backend and DevOps"))
                .andExpect(jsonPath("$.headline.es").value("Estudiante de Ingeniería de Software enfocado en backend y DevOps"))
                .andExpect(jsonPath("$.location").value("Quito, Ecuador"))
                .andExpect(jsonPath("$.githubUrl").value("https://github.com/ElianHidalgo"))
                .andExpect(jsonPath("$.linkedinUrl").value("https://www.linkedin.com/in/victor-hidalgo-889249303/"))
                .andExpect(jsonPath("$.cvUrl.es").value("cv/cv-es.pdf"))
                .andExpect(jsonPath("$.cvUrl.en").value("cv/cv-en.pdf"));
    }

    @Test
    void describesTheUdlaInternshipAsFinished() throws Exception {
        // The UDLA internship ended on 2026-11-12 (May–November 2026).
        mockMvc.perform(get("/api/profile"))
                .andExpect(jsonPath("$.about.es", allOf(
                        containsString("busco una pasantía en desarrollo backend, DevOps o infraestructura"),
                        containsString("Entre mayo y noviembre de 2026 hice mi pasantía en la UDLA"),
                        not(containsString("pasantía actual")))))
                .andExpect(jsonPath("$.about.en", allOf(
                        containsString("looking for an internship in backend development, DevOps or infrastructure"),
                        containsString("From May to November 2026 I interned at UDLA"),
                        not(containsString("current internship")))));
    }

    @Test
    void returnsSkillsOrderedByDisplayOrderThenName() throws Exception {
        jdbc.update("insert into profile.skill (name, category, display_order) values ('Kotlin', 'BACKEND', 1)");

        mockMvc.perform(get("/api/profile"))
                .andExpect(jsonPath("$.skills[0:3].name").value(contains("Java", "Kotlin", "Spring Boot")))
                .andExpect(jsonPath("$.skills.length()").value(21))
                .andExpect(jsonPath("$.skills[0].category").value("BACKEND"));
    }

    @Test
    void returnsNotFoundProblemWhenProfileIsMissing() throws Exception {
        jdbc.update("delete from profile.profile");

        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Profile has not been created yet"));
    }
}
