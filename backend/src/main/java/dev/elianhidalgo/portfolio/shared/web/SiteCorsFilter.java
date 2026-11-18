package dev.elianhidalgo.portfolio.shared.web;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Lets the public site (a different origin: Cloudflare) call the API from the browser.
 *
 * <p>Only the exact origins in {@code CORS_ALLOWED_ORIGINS} are allowed — never patterns or
 * {@code *} — and no credentials are shared: the public API needs no cookies.
 *
 * <p>A filter rather than MVC configuration, so it runs before the rate-limit and size filters:
 * their rejections (429, 411, 413) stay readable by the site, and preflights are answered here
 * without spending the client's requests.
 */
@Component
// Right after ApiHeadersFilter, so preflight answers carry the security headers too.
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SiteCorsFilter extends CorsFilter {

    public SiteCorsFilter(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        super(source(allowedOrigins));
    }

    private static UrlBasedCorsConfigurationSource source(List<String> allowedOrigins) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        config.setAllowCredentials(false);
        config.setMaxAge(Duration.ofHours(1));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
