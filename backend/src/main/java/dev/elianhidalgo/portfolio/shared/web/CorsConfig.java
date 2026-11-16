package dev.elianhidalgo.portfolio.shared.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the public site (a different origin: Cloudflare) call the API from the browser.
 *
 * <p>Only the exact origins in {@code CORS_ALLOWED_ORIGINS} are allowed — never patterns or
 * {@code *} — and no credentials are shared: the public API needs no cookies.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
