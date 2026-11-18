package dev.elianhidalgo.portfolio.auth;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.elianhidalgo.portfolio.TestTokens;
import dev.elianhidalgo.portfolio.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Only a valid admin token opens /api/admin/**; everything public stays public. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminAccessTests {

    @Autowired
    MockMvc mockMvc;

    private ResultActions session(String authorization) throws Exception {
        var request = get("/api/admin/session");
        return mockMvc.perform(authorization == null ? request : request.header("Authorization", authorization));
    }

    private ResultActions expectUnauthorized(ResultActions result) throws Exception {
        return result.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Authentication required"))
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void sessionRequiresAToken() throws Exception {
        expectUnauthorized(session(null));
    }

    @Test
    void validTokenOpensTheSession() throws Exception {
        session("Bearer " + TestTokens.valid())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String expired = TestTokens.signed(TestcontainersConfiguration.JWT_SECRET, "portfolio-api",
                Instant.now().minus(Duration.ofHours(3)), Duration.ofHours(2));
        expectUnauthorized(session("Bearer " + expired));
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() throws Exception {
        String forged = TestTokens.signed(TestTokens.OTHER_SECRET, "portfolio-api", Instant.now(), Duration.ofHours(2));
        expectUnauthorized(session("Bearer " + forged));
    }

    @Test
    void rejectsUnsignedToken() throws Exception {
        expectUnauthorized(session("Bearer " + TestTokens.unsigned()));
    }

    @Test
    void rejectsTokenFromAnotherIssuer() throws Exception {
        String foreign = TestTokens.signed(TestcontainersConfiguration.JWT_SECRET, "someone-else", Instant.now(), Duration.ofHours(2));
        expectUnauthorized(session("Bearer " + foreign));
    }

    @Test
    void rejectsMalformedAuthorizationHeader() throws Exception {
        expectUnauthorized(session("Bearer not-a-jwt"));
    }

    @Test
    void publicEndpointsStayPublicAndCacheable() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=60"));
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(post("/api/contact").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicEndpointsIgnoreABrokenBearerToken() throws Exception {
        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/contact").header("Authorization", "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preflightAllowsTheAuthorizationHeader() throws Exception {
        mockMvc.perform(options("/api/admin/session").header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("authorization")))
                .andExpect(header().string("Access-Control-Allow-Origin", not("*")));
    }
}
