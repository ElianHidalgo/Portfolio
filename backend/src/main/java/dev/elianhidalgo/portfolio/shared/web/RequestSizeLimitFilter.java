package dev.elianhidalgo.portfolio.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bounds what a client may upload: a POST to the API must declare its size, and at most 16 KiB.
 * Refusing before the body is read keeps a 512 MB instance safe from oversized payloads.
 */
@Component
// After the headers, CORS and rate-limit filters: a rejection still carries the security and CORS headers.
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final long MAX_BODY_BYTES = 16_384;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        if (length < 0) {
            reject(response, HttpStatus.LENGTH_REQUIRED, "Content-Length required", request);
        } else if (length > MAX_BODY_BYTES) {
            reject(response, HttpStatus.CONTENT_TOO_LARGE, "Request body too large", request);
        } else {
            chain.doFilter(request, response);
        }
    }

    private static void reject(HttpServletResponse response, HttpStatus status, String detail, HttpServletRequest request)
            throws IOException {
        response.setStatus(status.value());
        response.setHeader("Cache-Control", "no-store");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase() + "\",\"status\":"
                + status.value() + ",\"detail\":\"" + detail + "\",\"instance\":\""
                + request.getRequestURI().replace("\"", "") + "\"}");
    }
}
