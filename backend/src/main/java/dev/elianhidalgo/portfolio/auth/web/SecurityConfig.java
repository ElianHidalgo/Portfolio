package dev.elianhidalgo.portfolio.auth.web;

import dev.elianhidalgo.portfolio.auth.application.JwtSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless security: only {@code /api/admin/**} needs a valid admin token; everything else
 * stays public. No sessions or cookies are used, so CSRF does not apply. CORS and response
 * headers are handled by the shared filters, which run before this chain.
 */
@Configuration
class SecurityConfig {

    @Bean
    JwtDecoder jwtDecoder(JwtSettings settings) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(settings.key()).macAlgorithm(MacAlgorithm.HS256).build();
        // Signature (HS256 only), expiry and issuer are all checked.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtSettings.ISSUER));
        return decoder;
    }

    /**
     * Reads bearer tokens only on admin paths: a stale or broken token sent to a public
     * endpoint is ignored instead of turning a public answer into a 401.
     */
    static BearerTokenResolver adminPathsOnly() {
        var standard = new DefaultBearerTokenResolver();
        return request -> request.getRequestURI().startsWith("/api/admin/") ? standard.resolve(request) : null;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var problems = new ProblemAuthenticationHandlers();
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                // ApiHeadersFilter owns the response headers (public GETs stay cacheable).
                .headers(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(adminPathsOnly())
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .build();
    }
}
