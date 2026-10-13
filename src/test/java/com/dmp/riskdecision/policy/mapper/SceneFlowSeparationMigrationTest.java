package com.dmp.riskdecision.policy.mapper;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证场景流程迁为独立流程资产时保留历史数据及发布指针。 */
class SceneFlowSeparationMigrationTest {
    @Test
    void migratesExistingSceneFlowsToIndependentAssets() throws Exception {
        String database = "flow_separation_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + database + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target("3").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO tb_scene(scene_code, scene_name, published_version_id) VALUES('risk', '风险决策', 41)");
            statement.executeUpdate("INSERT INTO tb_scene(scene_code, scene_name) VALUES('market', '营销决策')");
            statement.executeUpdate("INSERT INTO tb_flow_version(id, scene_code, version_number, graph_json, chain_id, compiled_el) "
                    + "VALUES(41, 'risk', 1, '{}', 'scene_risk_v1', 'THEN(emitValue)')");
            statement.executeUpdate("INSERT INTO tb_flow_draft(scene_code, graph_json, revision) VALUES('risk', '{}', 3)");
        }

        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target("4").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("""
                    SELECT s.flow_id, f.flow_code, f.flow_name, s.published_version_id,
                           d.flow_id AS draft_flow_id, d.revision,
                           v.flow_id AS version_flow_id, v.id AS version_id
                    FROM tb_scene s
                    JOIN tb_flow f ON f.id = s.flow_id
                    JOIN tb_flow_draft d ON d.flow_id = f.id
                    JOIN tb_flow_version v ON v.id = s.published_version_id
                    WHERE s.scene_code = 'risk'
                    """)) {
                assertThat(result.next()).isTrue();
                long flowId = result.getLong("flow_id");
                assertThat(flowId).isPositive();
                assertThat(result.getString("flow_code")).isEqualTo("risk");
                assertThat(result.getString("flow_name")).isEqualTo("风险决策");
                assertThat(result.getLong("published_version_id")).isEqualTo(41);
                assertThat(result.getLong("draft_flow_id")).isEqualTo(flowId);
                assertThat(result.getLong("version_flow_id")).isEqualTo(flowId);
                assertThat(result.getLong("version_id")).isEqualTo(41);
                assertThat(result.getLong("revision")).isEqualTo(3);
            }
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM tb_flow")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(2);
            }
            try (ResultSet result = statement.executeQuery("""
                    SELECT f.id FROM tb_flow f JOIN tb_scene s ON s.flow_id = f.id
                    WHERE s.scene_code = 'market' AND f.flow_code = 'market'
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isPositive();
            }
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet columns = metadata.getColumns(null, null, "tb_flow_draft", "scene_code")) {
                assertThat(columns.next()).isFalse();
            }
            try (ResultSet columns = metadata.getColumns(null, null, "tb_flow_version", "scene_code")) {
                assertThat(columns.next()).isFalse();
            }
        }
    }
}
