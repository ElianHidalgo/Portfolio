package dev.elianhidalgo.portfolio.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.elianhidalgo.portfolio.TestcontainersConfiguration;
import dev.elianhidalgo.portfolio.auth.application.AdminAccountInitializer;
import dev.elianhidalgo.portfolio.auth.application.AdminCredentials;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The admin account always matches the ADMIN_* environment, and secrets never reach the log. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AdminAccountInitializerTests {

    private static final AdminCredentials CONFIGURED =
            new AdminCredentials(TestcontainersConfiguration.ADMIN_USERNAME, TestcontainersConfiguration.ADMIN_PASSWORD);

    @Autowired
    AdminAccountInitializer initializer;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    PostgreSQLContainer postgres;

    @Autowired
    ApplicationContext context;

    private JdbcTemplate owner;

    @BeforeEach
    void startFromTheConfiguredAdmin() {
        owner = new JdbcTemplate(new SingleConnectionDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true));
        owner.update("delete from auth.admin_user");
        initializer.sync(CONFIGURED);
    }

    private List<Map<String, Object>> accounts() {
        return owner.queryForList("select username, password_hash, updated_at from auth.admin_user");
    }

    @Test
    void createsTheConfiguredAdminWithABcryptHash() {
        assertThat(accounts()).singleElement().satisfies(account -> {
            assertThat(account.get("username")).isEqualTo("admin");
            String hash = (String) account.get("password_hash");
            assertThat(hash).startsWith("$2");
            assertThat(passwordEncoder.matches(TestcontainersConfiguration.ADMIN_PASSWORD, hash)).isTrue();
        });
    }

    @Test
    void doesNotRehashWhenThePasswordIsUnchanged() {
        Map<String, Object> before = accounts().get(0);

        initializer.sync(CONFIGURED);

        assertThat(accounts()).containsExactly(before);
    }

    @Test
    void rehashesWhenTheEnvPasswordChanges() {
        initializer.sync(new AdminCredentials("admin", "another-strong-password"));

        String hash = (String) accounts().get(0).get("password_hash");
        assertThat(passwordEncoder.matches("another-strong-password", hash)).isTrue();
        assertThat(passwordEncoder.matches(TestcontainersConfiguration.ADMIN_PASSWORD, hash)).isFalse();
    }

    @Test
    void keepsOnlyTheConfiguredUsername() {
        owner.update("insert into auth.admin_user(username, password_hash) values ('old-admin', 'x')");

        initializer.sync(CONFIGURED);

        assertThat(accounts()).extracting(account -> account.get("username")).containsExactly("admin");
    }

    @Test
    void skipsWeakOrMissingCredentialsWithoutLeakingThem(CapturedOutput output) {
        owner.update("delete from auth.admin_user");

        initializer.sync(new AdminCredentials("admin", "Tiny1"));
        assertThat(accounts()).isEmpty();
        assertThat(output).contains("ADMIN_PASSWORD").doesNotContain("Tiny1");

        initializer.sync(new AdminCredentials("admin", "ñ".repeat(37)));
        assertThat(accounts()).isEmpty();
        assertThat(output).doesNotContain("ñññ");

        initializer.sync(new AdminCredentials("", ""));
        assertThat(accounts()).isEmpty();
        assertThat(output).contains("No admin configured");
    }

    @Test
    void invalidOrEmptyCredentialsDisableAnExistingAdmin() {
        // A leaked password must not keep working after the env is cleared or set to an invalid value.
        initializer.sync(new AdminCredentials("admin", "Tiny1"));
        assertThat(accounts()).isEmpty();

        initializer.sync(CONFIGURED);
        initializer.sync(new AdminCredentials("", ""));
        assertThat(accounts()).isEmpty();
    }

    @Test
    void springDoesNotCreateItsOwnDefaultUser() {
        // Otherwise Spring Boot adds an in-memory "user" and logs its generated password.
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
    }
}
