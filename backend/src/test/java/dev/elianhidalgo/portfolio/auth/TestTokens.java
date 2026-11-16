package dev.elianhidalgo.portfolio.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.elianhidalgo.portfolio.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Mints JWTs for tests, valid or deliberately broken. */
final class TestTokens {

    static final String OTHER_SECRET = Base64.getEncoder().encodeToString("another-48-byte-secret-for-forged-tokens-0000000".getBytes(StandardCharsets.UTF_8));

    private TestTokens() {}

    static String valid() {
        return signed(TestcontainersConfiguration.JWT_SECRET, "portfolio-api", Instant.now(), Duration.ofHours(2));
    }

    static String signed(String base64Secret, String issuer, Instant issuedAt, Duration lifetime) {
        var key = new SecretKeySpec(Base64.getDecoder().decode(base64Secret), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(TestcontainersConfiguration.ADMIN_USERNAME)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(lifetime))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    /** {"alg":"none"} with valid claims and no signature: must never be accepted. */
    static String unsigned() {
        var url = Base64.getUrlEncoder().withoutPadding();
        long now = Instant.now().getEpochSecond();
        String header = url.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = url.encodeToString(("{\"iss\":\"portfolio-api\",\"sub\":\"admin\",\"iat\":" + now
                + ",\"exp\":" + (now + 3600) + "}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }
}
