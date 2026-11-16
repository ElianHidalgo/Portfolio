package dev.elianhidalgo.portfolio.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.elianhidalgo.portfolio.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = "app.rate-limit.requests-per-minute=5")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitTests {

    @Autowired
    MockMvc mockMvc;

    /** A request as it reaches Render: Cloudflare sets CF-Connecting-IP to the real client. */
    private static MockHttpServletRequestBuilder from(String clientIp, String path) {
        return get(path).header("CF-Connecting-IP", clientIp);
    }

    @Test
    void limitsRequestsPerClient() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(from("203.0.113.1", "/api/projects")).andExpect(status().isOk());
        }
        mockMvc.perform(from("203.0.113.1", "/api/projects"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Too many requests"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void clientsAreLimitedIndependently() throws Exception {
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(from("203.0.113.2", "/api/projects"));
        }
        mockMvc.perform(from("203.0.113.3", "/api/projects")).andExpect(status().isOk());
    }

    @Test
    void ignoresSpoofedForwardedForHeader() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(from("203.0.113.4", "/api/profile").header("X-Forwarded-For", "198.51.100." + i))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(from("203.0.113.4", "/api/profile").header("X-Forwarded-For", "198.51.100.99"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void healthIsNotLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(from("203.0.113.5", "/api/health")).andExpect(status().isOk());
        }
    }

    @Test
    void rejectionCarriesCorsHeaderForTheSite() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(from("203.0.113.40", "/api/projects"));
        }
        mockMvc.perform(from("203.0.113.40", "/api/projects").header("Origin", "http://localhost:4200"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    void preflightsDoNotSpendTheClientsRequests() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(options("/api/contact").header("CF-Connecting-IP", "203.0.113.41")
                            .header("Origin", "http://localhost:4200").header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(from("203.0.113.41", "/api/projects")).andExpect(status().isOk());
    }
}
