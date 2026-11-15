package dev.elianhidalgo.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The app's database role can use data but never change the schema (least privilege). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatabasePrivilegesTests {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Autowired
    PostgreSQLContainer postgres;

    private Connection asAppRole() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(),
                TestcontainersConfiguration.APP_ROLE, TestcontainersConfiguration.APP_PASSWORD);
    }

    @Test
    void tablesLiveInTheirModuleSchema() {
        var owner = new JdbcTemplate(new SingleConnectionDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true));
        List<String> tables = owner.queryForList(
                "select table_schema || '.' || table_name from information_schema.tables "
                        + "where table_schema in ('public', 'profile', 'projects') and table_type = 'BASE TABLE' "
                        + "order by 1", String.class);

        assertThat(tables).containsExactly(
                "profile.profile", "profile.skill", "projects.project", "public.flyway_schema_history");
    }

    @Test
    void runtimeRoleCannotChangeTheSchema() throws SQLException {
        try (Connection connection = asAppRole(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute("create table projects.injected(id int)"))
                    .isInstanceOf(SQLException.class)
                    .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThatThrownBy(() -> statement.execute("drop table profile.skill"))
                    .isInstanceOf(SQLException.class)
                    .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    void runtimeRoleCanReadAndWriteData() throws SQLException {
        try (Connection connection = asAppRole(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            assertThat(statement.executeQuery("select count(*) from profile.skill").next()).isTrue();
            assertThat(statement.executeUpdate(
                    "update projects.project set title_en = title_en where slug = 'gamevault'")).isEqualTo(1);
            connection.rollback();
        }
    }
}
