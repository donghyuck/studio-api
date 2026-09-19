package studio.one.platform.ai.service.visualization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPoint;

class JdbcVectorProjectionPointRepositoryTest {

    private JdbcVectorProjectionPointRepository repository;

    @BeforeEach
    void setUp() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.getJdbcTemplate()).thenReturn(mock(JdbcTemplate.class));
        repository = new JdbcVectorProjectionPointRepository(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void countWithoutChunkFiltersUsesProjectionPointTableOnly() {
        assertThat(repository.countSql(null, null, " AND p.cluster_id = :clusterId"))
                .contains("FROM tb_ai_vector_projection_point p")
                .contains("p.cluster_id = :clusterId")
                .doesNotContain("tb_ai_document_chunk");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void saveAllUsesDialectAppropriateJsonBinding(boolean postgres) {
        NamedParameterJdbcTemplate template = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate operations = mock(JdbcTemplate.class);
        when(template.getJdbcOperations()).thenReturn(operations);
        when(operations.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Boolean>>any()))
                .thenReturn(postgres);
        var pointRepository = new JdbcVectorProjectionPointRepository(template, new ObjectMapper());
        pointRepository.saveAll(List.of(new VectorProjectionPoint("projection", "row-1", 1L,
                "attachment", "1", "sample", Map.of("title", "sample"), 1.0, 2.0, null, 0, null)));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(template).batchUpdate(sql.capture(), any(SqlParameterSource[].class));
        if (postgres) {
            assertThat(sql.getValue()).contains("CAST(:metadataPreview AS jsonb)");
        } else {
            assertThat(sql.getValue()).contains(":metadataPreview").doesNotContain("AS jsonb");
        }
    }

    @Test
    void countWithPointFiltersUsesProjectionPointTableOnly() {
        assertThat(repository.countSql("attachment", "doc", " AND p.target_type = :targetType"))
                .contains("FROM tb_ai_vector_projection_point p")
                .contains("p.target_type = :targetType")
                .doesNotContain("tb_ai_document_chunk");
    }
}
