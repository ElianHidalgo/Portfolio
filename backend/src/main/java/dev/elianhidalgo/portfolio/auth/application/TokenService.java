package dev.elianhidalgo.portfolio.auth.application;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/** Issues admin tokens: HS256, issuer {@value JwtSettings#ISSUER}, valid for two hours. */
@Service
public class TokenService {

    private final JwtEncoder encoder;

    public TokenService(JwtSettings settings) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(settings.key()));
    }

    public IssuedToken issue(String username) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(JwtSettings.LIFETIME);
        var claims = JwtClaimsSet.builder()
                .issuer(JwtSettings.ISSUER)
                .subject(username)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
    }
}
