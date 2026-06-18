package studio.one.platform.ai.core.vector.visualization;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VectorProjectionMigrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"postgres", "mysql", "mariadb"})
    void migrationAddsProjectionSamplingMetadata(String database) throws IOException {
        String resource = "schema/ai/" + database + "/V606__add_vector_projection_scope_metadata.sql";
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql)
                    .contains("mode")
                    .contains("total_count")
                    .contains("projected_count")
                    .contains("sampled")
                    .contains("sample_size")
                    .contains("sampling_strategy")
                    .contains("max_allowed")
                    .contains("error_code")
                    .contains("SET total_count = item_count")
                    .contains("projected_count = item_count");
        }
    }

    @org.junit.jupiter.api.Test
    void postgresMigrationIndexesProjectionPointJoinExpression() throws IOException {
        String resource = "schema/ai/postgres/V607__index_vector_projection_point_join.sql";
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql)
                    .contains("idx_ai_chunk_vector_item_id")
                    .contains("metadata ->> 'chunkId'")
                    .contains("'row-' || id");
        }
    }
}
