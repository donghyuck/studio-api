package studio.one.platform.ai.model.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModelDeploymentIdentityMigrationContractTest {

    @ParameterizedTest
    @ValueSource(strings = {"postgres", "mysql", "mariadb"})
    void aiMigrationAddsOperationalIdentityAndMigrationState(String database) throws Exception {
        String sql = resource("schema/ai/" + database + "/V619__expand_model_deployment_identity.sql");

        assertThat(sql)
                .contains("model_deployment_id")
                .contains("catalog_id")
                .contains("embedding_space_id")
                .contains("tb_ai_model_data_migration");
    }

    private String resource(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
