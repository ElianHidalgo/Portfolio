package dev.elianhidalgo.portfolio.auth.application;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** The admin account as configured in the environment (ADMIN_USERNAME, ADMIN_PASSWORD). */
public record AdminCredentials(String username, String password) {

    /** BCrypt only reads the first 72 bytes of a password. */
    static final int MAX_PASSWORD_BYTES = 72;
    static final int MIN_PASSWORD_LENGTH = 12;

    public AdminCredentials {
        username = username == null ? "" : username.strip();
        password = password == null ? "" : password;
    }

    boolean isBlank() {
        return username.isEmpty() && password.isEmpty();
    }

    /** Why these credentials cannot be used, if they cannot. Never includes the values themselves. */
    Optional<String> problem() {
        if (username.length() < 3 || username.length() > 50) {
            return Optional.of("ADMIN_USERNAME must have 3 to 50 characters");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return Optional.of("ADMIN_PASSWORD must have at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (exceedsBcryptLimit(password)) {
            return Optional.of("ADMIN_PASSWORD must be at most " + MAX_PASSWORD_BYTES + " bytes (UTF-8)");
        }
        return Optional.empty();
    }

    static boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }
}
