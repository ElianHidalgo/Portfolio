package dev.elianhidalgo.portfolio.auth.application;

import dev.elianhidalgo.portfolio.auth.domain.AdminAccount;
import dev.elianhidalgo.portfolio.auth.domain.AdminAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the admin account in line with the environment at every startup: the env is the
 * source of truth, so rotating the password only takes a change in Render and a redeploy.
 * Invalid or missing credentials leave no admin (the public site keeps working) and are
 * reported without ever logging the values.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AdminAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final AdminCredentials configured;

    public AdminAccountInitializer(AdminAccountRepository accounts, PasswordEncoder passwordEncoder,
            @Value("${app.auth.admin.username:}") String username, @Value("${app.auth.admin.password:}") String password) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.configured = new AdminCredentials(username, password);
    }

    // Transactional here too: run() calls sync() directly, which bypasses the proxy on sync().
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        sync(configured);
    }

    @Transactional
    public void sync(AdminCredentials credentials) {
        if (credentials.isBlank()) {
            log.info("No admin configured (ADMIN_USERNAME and ADMIN_PASSWORD are empty): the admin area is disabled");
            return;
        }
        var problem = credentials.problem();
        if (problem.isPresent()) {
            log.warn("Admin account not updated: {}", problem.get());
            return;
        }
        accounts.deleteByUsernameNot(credentials.username());
        accounts.findByUsername(credentials.username()).ifPresentOrElse(
                account -> {
                    if (!passwordEncoder.matches(credentials.password(), account.getPasswordHash())) {
                        account.changePassword(passwordEncoder.encode(credentials.password()));
                        log.info("Admin password updated from the environment");
                    }
                },
                () -> {
                    accounts.save(new AdminAccount(credentials.username(), passwordEncoder.encode(credentials.password())));
                    log.info("Admin account created from the environment");
                });
    }
}
