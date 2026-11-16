package dev.elianhidalgo.portfolio.auth.application;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The key that signs admin tokens (HS256), from {@code JWT_SECRET}.
 *
 * <p>A missing or weak secret stops the application from starting: on Render the new deploy
 * fails its health check and the previous version keeps serving, instead of running with a
 * key an attacker could guess.
 */
@Component
public class JwtSettings {

    public static final String ISSUER = "portfolio-api";
    public static final Duration LIFETIME = Duration.ofHours(2);
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;

    public JwtSettings(@Value("${app.auth.jwt-secret:}") String base64Secret) {
        this.key = new SecretKeySpec(decode(base64Secret), "HmacSHA256");
    }

    public SecretKey key() {
        return key;
    }

    private static byte[] decode(String base64Secret) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Secret == null ? "" : base64Secret.strip());
        } catch (IllegalArgumentException notBase64) {
            bytes = new byte[0];
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be a base64 value of at least 32 bytes");
        }
        return bytes;
    }
}
