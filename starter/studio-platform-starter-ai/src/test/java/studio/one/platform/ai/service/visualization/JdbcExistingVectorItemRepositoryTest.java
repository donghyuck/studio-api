package studio.one.platform.ai.service.visualization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcExistingVectorItemRepositoryTest {

    private JdbcExistingVectorItemRepository repository;

    @BeforeEach
    void setUp() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.getJdbcTemplate()).thenReturn(mock(JdbcTemplate.class));
        repository = new JdbcExistingVectorItemRepository(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void projectionVectorItemIdPrefersChunkId() {
        assertThat(repository.projectionVectorItemId(Map.of("chunkId", "chunk-1234"), "123"))
                .isEqualTo("chunk-1234");
    }

    @Test
    void projectionVectorItemIdFallsBackToRowIdWhenChunkIdIsBlank() {
        assertThat(repository.projectionVectorItemId(Map.of("chunkId", "  "), "123"))
                .isEqualTo("row-123");
    }
}
