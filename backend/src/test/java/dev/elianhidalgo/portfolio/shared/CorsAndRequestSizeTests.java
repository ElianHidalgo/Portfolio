package dev.elianhidalgo.portfolio.shared;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** The browser may call the API only from the site's origin, and POST bodies are bounded. */
@SpringBootTest(properties = "app.cors.allowed-origins=https://site.example")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CorsAndRequestSizeTests {

    private static final String SITE = "https://site.example";

    @Autowired
    MockMvc mockMvc;

    @Test
    void allowsPreflightFromSiteOrigin() throws Exception {
        mockMvc.perform(options("/api/contact").header("Origin", SITE)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", SITE))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void rejectsPreflightFromUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/contact").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void publicGetCarriesCorsHeaderForSiteOrigin() throws Exception {
        mockMvc.perform(get("/api/profile").header("Origin", SITE))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", SITE));
    }

    @Test
    void rejectsOversizedPostBody() throws Exception {
        mockMvc.perform(post("/api/contact").contentType(APPLICATION_JSON).content("x".repeat(16_385)))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Request body too large"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void requiresContentLengthOnPost() throws Exception {
        // No body set: MockMvc reports Content-Length -1, as for a chunked upload of unknown size.
        mockMvc.perform(post("/api/contact").contentType(APPLICATION_JSON))
                .andExpect(status().isLengthRequired())
                .andExpect(jsonPath("$.detail").value("Content-Length required"));
    }
}
