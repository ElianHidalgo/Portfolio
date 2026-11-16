package dev.elianhidalgo.portfolio.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.elianhidalgo.portfolio.TestcontainersConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "app.auth.login.attempts=3")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class LoginApiTests {

    private static final String RIGHT = TestcontainersConfiguration.ADMIN_PASSWORD;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtDecoder jwtDecoder;

    /** A login as it reaches Render: Cloudflare sets CF-Connecting-IP to the real client. */
    private ResultActions login(String clientIp, String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return mockMvc.perform(post("/api/auth/login").header("CF-Connecting-IP", clientIp)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void issuesATwoHourTokenForTheAdmin() throws Exception {
        String response = login("203.0.113.50", "admin", RIGHT)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(response, "$.token");

        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("admin");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("portfolio-api");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(2));
        mockMvc.perform(get("/api/admin/session").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownUserLookTheSame() throws Exception {
        String wrongPassword = login("203.0.113.51", "admin", "not-the-password")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"))
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andReturn().getResponse().getContentAsString();
        String unknownUser = login("203.0.113.51", "nobody", "not-the-password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownUser).isEqualTo(wrongPassword);
    }

    @Test
    void overlongPasswordIsJustInvalid() throws Exception {
        login("203.0.113.52", "admin", "ñ".repeat(40))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void rejectsBlankFields() throws Exception {
        login("203.0.113.53", "", "")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void limitsLoginAttemptsPerClient() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("203.0.113.54", "admin", "wrong-" + i).andExpect(status().isUnauthorized());
        }
        login("203.0.113.54", "admin", RIGHT)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value("Too many login attempts, try again later"));

        login("203.0.113.55", "admin", RIGHT).andExpect(status().isOk());
    }

    @Test
    void neverLogsThePassword(CapturedOutput output) throws Exception {
        login("203.0.113.56", "admin", "wrong-secret-value");
        login("203.0.113.56", "admin", RIGHT);

        assertThat(output).doesNotContain("wrong-secret-value").doesNotContain(RIGHT);
    }
}
