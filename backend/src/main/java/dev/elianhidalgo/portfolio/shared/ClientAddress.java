package dev.elianhidalgo.portfolio.shared;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Identifies the client behind a request.
 *
 * <p>Render sits behind Cloudflare, which sets {@code CF-Connecting-IP} to the real visitor
 * address and overwrites any value a client sends. {@code X-Forwarded-For} is never trusted,
 * since a client can put anything in it. Without Cloudflare (local runs) the socket address is used.
 */
public final class ClientAddress {

    private ClientAddress() {}

    public static String of(HttpServletRequest request) {
        String cloudflareClientIp = request.getHeader("CF-Connecting-IP");
        return cloudflareClientIp != null && !cloudflareClientIp.isBlank() ? cloudflareClientIp : request.getRemoteAddr();
    }
}
