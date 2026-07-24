package studio.one.platform.skillgraph.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModelDeploymentIdentityMigrationContractTest {

    @ParameterizedTest
    @ValueSource(strings = {"postgres", "mysql", "mariadb"})
    void migrationExpandsTablesAvailableInEveryDialect(String database) throws Exception {
        String sql = resource("schema/skillgraph/" + database
                + "/V1521__expand_model_deployment_identity.sql");

        assertThat(sql)
                .contains("ALTER TABLE tb_skill_embedding")
                .contains("model_deployment_id")
                .contains("catalog_id")
                .contains("embedding_space_id");
        if ("postgres".equals(database)) {
            assertThat(sql).contains("ALTER TABLE tb_skill_dataset_concept_embedding");
        } else {
            assertThat(sql).doesNotContain("tb_skill_dataset_concept_embedding");
        }
    }

    private String resource(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
