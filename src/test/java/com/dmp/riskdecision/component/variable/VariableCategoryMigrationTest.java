package com.dmp.riskdecision.component.variable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 V9 对全部旧变量码回填分类，且不重写任何取值版本。 */
class VariableCategoryMigrationTest {
    @Test
    void backfillsKnownAndUnknownCodesAcrossHistoricalVersions() throws Exception {
        String url = "jdbc:h2:mem:variable_category_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway before = Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("8")).load();
        before.migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insert(statement, "demoRemainingBudget", 1);
            insert(statement, "demoRemainingBudget", 2);
            insert(statement, "legacyUnclassified", 1);
            assertThat(count(statement, "SELECT COUNT(*) FROM tb_variable_version")).isEqualTo(3);
        }
        Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").load().migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertThat(count(statement, "SELECT COUNT(*) FROM tb_variable_version")).isEqualTo(3);
            assertThat(count(statement, "SELECT COUNT(*) FROM tb_variable_category")).isEqualTo(2);
            assertThat(category(statement, "demoRemainingBudget")).isEqualTo("BUDGET");
            assertThat(category(statement, "legacyUnclassified")).isEqualTo("OTHER");
        }
    }

    private void insert(Statement statement, String code, int version) throws Exception {
        statement.executeUpdate("INSERT INTO tb_variable_version "
                + "(variable_code, variable_name, version_number, value_type, source_type, url, timeout_ms, "
                + "parameter_names_json, result_path) VALUES ('" + code + "', '旧变量', " + version
                + ", 'STRING', 'HTTP_GET', 'http://localhost/value', 1000, '[]', 'value')");
    }

    private long count(Statement statement, String sql) throws Exception {
        try (ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private String category(Statement statement, String code) throws Exception {
        try (ResultSet rows = statement.executeQuery("SELECT category_code FROM tb_variable_category "
                + "WHERE variable_code = '" + code + "'")) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }
}
