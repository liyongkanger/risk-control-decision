package com.dmp.riskdecision.policy.mapper;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证旧流程表升级到规范表结构后保留业务数据。 */
class SceneFlowMigrationTest {
    @Test
    void migratesExistingPublishedSceneAndDraftToStandardTables() throws Exception {
        String database = "risk_migration_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + database + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target("1").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO flow_version(id, scene_code, version_number, graph_json, chain_id, compiled_el, created_at)
                    VALUES(41, 'payment', 1, '{}', 'scene_payment_v1', 'THEN(emitValue)', CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO scene(scene_code, published_version_id, updated_at)
                    VALUES('payment', 41, CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO flow_draft(scene_code, graph_json, revision, updated_at)
                    VALUES('payment', '{}', 3, CURRENT_TIMESTAMP)
                    """);
        }

        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target("2").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertStandardColumns(connection, "tb_scene");
            assertStandardColumns(connection, "tb_flow_draft");
            assertStandardColumns(connection, "tb_flow_version");

            try (ResultSet result = statement.executeQuery("""
                    SELECT s.id, s.scene_code, s.published_version_id, s.inserttime, s.updatetime, s.isactive,
                           d.id AS draft_id, d.revision, v.id AS version_id, v.isactive AS version_active
                    FROM tb_scene s
                    JOIN tb_flow_draft d ON d.scene_code = s.scene_code
                    JOIN tb_flow_version v ON v.id = s.published_version_id
                    WHERE s.scene_code = 'payment'
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong("id")).isPositive();
                assertThat(result.getLong("draft_id")).isPositive();
                assertThat(result.getLong("published_version_id")).isEqualTo(41);
                assertThat(result.getLong("version_id")).isEqualTo(41);
                assertThat(result.getLong("revision")).isEqualTo(3);
                assertThat(result.getTimestamp("inserttime")).isNotNull();
                assertThat(result.getTimestamp("updatetime")).isNotNull();
                assertThat(result.getInt("isactive")).isEqualTo(1);
                assertThat(result.getInt("version_active")).isEqualTo(1);
                assertThat(result.next()).isFalse();
            }
            statement.executeUpdate("INSERT INTO tb_scene(scene_code) VALUES('new_scene')");
            try (ResultSet result = statement.executeQuery(
                    "SELECT id, inserttime, updatetime, isactive FROM tb_scene WHERE scene_code='new_scene'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong("id")).isPositive();
                assertThat(result.getTimestamp("inserttime")).isNotNull();
                assertThat(result.getTimestamp("updatetime")).isNotNull();
                assertThat(result.getInt("isactive")).isEqualTo(1);
            }
        }
    }

    private void assertStandardColumns(Connection connection, String table) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> columns = new HashSet<>();
        try (ResultSet result = metadata.getColumns(null, null, table, null)) {
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME"));
            }
        }
        assertThat(columns).contains("id", "inserttime", "updatetime", "isactive");

        try (ResultSet result = metadata.getPrimaryKeys(null, null, table)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("COLUMN_NAME")).isEqualTo("id");
            assertThat(result.next()).isFalse();
        }
    }
}
