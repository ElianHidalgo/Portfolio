package dev.elianhidalgo.portfolio.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Security and cache headers for every API response. Public content (profile, projects) may
 * be cached by browsers for a minute; everything else — errors, and later contact and admin
 * endpoints — must never be stored.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiHeadersFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", isPublicContent(request) ? "public, max-age=60" : "no-store");
        chain.doFilter(request, response);
    }

    private static boolean isPublicContent(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "GET".equals(request.getMethod())
                && (path.equals("/api/profile") || path.equals("/api/projects") || path.startsWith("/api/projects/"));
    }
}
