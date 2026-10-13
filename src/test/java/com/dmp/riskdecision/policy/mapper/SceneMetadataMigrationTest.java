package com.dmp.riskdecision.policy.mapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证场景基本信息迁移保留历史场景。 */
class SceneMetadataMigrationTest {
    @Test
    void backfillsNamesForExistingScenes() throws Exception {
        String database = "scene_metadata_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + database + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target("2").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO tb_scene(scene_code) VALUES('legacyScene')");
        }

        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target("3").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT scene_name, description FROM tb_scene WHERE scene_code='legacyScene'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("scene_name")).isEqualTo("legacyScene");
            assertThat(result.getString("description")).isNull();
        }
    }
}
