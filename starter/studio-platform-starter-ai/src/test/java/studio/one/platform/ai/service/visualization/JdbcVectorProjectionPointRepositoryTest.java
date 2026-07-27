package studio.one.platform.ai.service.visualization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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

    @Test
    void countWithPointFiltersUsesProjectionPointTableOnly() {
        assertThat(repository.countSql("attachment", "doc", " AND p.target_type = :targetType"))
                .contains("FROM tb_ai_vector_projection_point p")
                .contains("p.target_type = :targetType")
                .doesNotContain("tb_ai_document_chunk");
    }
}
