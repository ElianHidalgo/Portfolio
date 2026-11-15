package dev.elianhidalgo.portfolio.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                .andExpect(jsonPath("$.detail").value("Too many requests"));
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
}
