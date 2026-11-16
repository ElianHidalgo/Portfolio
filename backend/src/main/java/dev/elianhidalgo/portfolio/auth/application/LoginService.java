package dev.elianhidalgo.portfolio.auth.application;

import dev.elianhidalgo.portfolio.auth.domain.AdminAccount;
import dev.elianhidalgo.portfolio.auth.domain.AdminAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks the admin's credentials and issues a token. Every failure looks and costs the same:
 * an unknown username still runs a BCrypt comparison, so neither the answer nor its timing
 * reveals which usernames exist.
 */
@Service
public class LoginService {

    private final AdminAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    /** Compared against when the username is unknown, to spend the same BCrypt work. */
    private final String dummyHash;

    public LoginService(AdminAccountRepository accounts, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public IssuedToken login(String username, String password) {
        // BCrypt only accepts 72 bytes; a longer password can never be the admin's (see AdminCredentials).
        if (AdminCredentials.exceedsBcryptLimit(password)) {
            throw new InvalidCredentialsException();
        }
        Optional<AdminAccount> account = accounts.findByUsername(username);
        String hash = account.map(AdminAccount::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(password, hash);
        if (account.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }
        return tokens.issue(account.get().getUsername());
    }
}
