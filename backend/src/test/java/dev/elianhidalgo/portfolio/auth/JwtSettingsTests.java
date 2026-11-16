package dev.elianhidalgo.portfolio.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.elianhidalgo.portfolio.TestcontainersConfiguration;
import dev.elianhidalgo.portfolio.auth.application.JwtSettings;
import org.junit.jupiter.api.Test;

class JwtSettingsTests {

    @Test
    void rejectsMissingShortOrNonBase64Secrets() {
        for (String secret : new String[] {"", "c2hvcnQ=", "not base64!!"}) {
            assertThatThrownBy(() -> new JwtSettings(secret))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("JWT_SECRET must be a base64 value of at least 32 bytes");
        }
    }

    @Test
    void acceptsA32ByteSecret() {
        assertThat(new JwtSettings(TestcontainersConfiguration.JWT_SECRET).key().getEncoded()).hasSize(48);
    }
}
