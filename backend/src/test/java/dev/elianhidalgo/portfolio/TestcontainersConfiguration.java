package dev.elianhidalgo.portfolio;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL for tests, wired like production: Flyway migrates as the owner (the container's
 * superuser) while the application connects as the data-only role {@value #APP_ROLE}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	public static final String APP_ROLE = "portfolio_app";
	public static final String APP_PASSWORD = "test";
	public static final String ADMIN_USERNAME = "admin";
	public static final String ADMIN_PASSWORD = "test-admin-password";
	/** 48 fixed bytes, base64: test-only signing key for JWTs. */
	public static final String JWT_SECRET = "dGVzdC1vbmx5LWp3dC1zZWNyZXQtd2l0aC1hdC1sZWFzdC0zMi1ieXRlcy0xMjM0";

	@Bean
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
				.withInitScript("db/create-app-role.sql");
	}

	@Bean
	DynamicPropertyRegistrar databaseProperties(PostgreSQLContainer postgres) {
		return registry -> {
			registry.add("spring.datasource.url", postgres::getJdbcUrl);
			registry.add("spring.datasource.username", () -> APP_ROLE);
			registry.add("spring.datasource.password", () -> APP_PASSWORD);
			registry.add("spring.flyway.user", postgres::getUsername);
			registry.add("spring.flyway.password", postgres::getPassword);
			registry.add("app.auth.admin.username", () -> ADMIN_USERNAME);
			registry.add("app.auth.admin.password", () -> ADMIN_PASSWORD);
			registry.add("app.auth.jwt-secret", () -> JWT_SECRET);
		};
	}

}
