package studio.one.platform.ai.service.visualization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.sql.ResultSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import studio.one.platform.ai.core.vector.visualization.ProjectionSamplingStrategy;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionScope;

class JdbcExistingVectorItemRepositoryTest {

    private JdbcExistingVectorItemRepository repository;
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.getJdbcTemplate()).thenReturn(mock(JdbcTemplate.class));
        repository = new JdbcExistingVectorItemRepository(jdbcTemplate, new ObjectMapper());
    }

    @ParameterizedTest
    @EnumSource(ProjectionSamplingStrategy.class)
    void everyItemSamplingQuerySelectsDimensionRequiredByRowMapper(ProjectionSamplingStrategy strategy) {
        repository.findItems(new VectorProjectionScope(List.of(), Map.of()), strategy, 10);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<VectorItem>>any());
        // STRATIFIED has both an inner and outer SELECT; both must expose the column.
        String[] selects = sql.getValue().split("SELECT");
        for (int index = 1; index < selects.length; index++) {
            assertThat(selects[index].substring(0, selects[index].indexOf("FROM")))
                    .as("%s SELECT %s must expose the mapper's dimension column", strategy, index)
                    .contains("embedding_dimension");
        }
    }

    @Test
    void itemMappingOmitsNullMetadataWithoutLosingDimension() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("metadata")).thenReturn("{\"title\":null,\"sourceName\":\"sample\"}");
        when(rs.getString("object_type")).thenReturn("attachment");
        when(rs.getString("object_id")).thenReturn("1");
        when(rs.getObject("embedding")).thenReturn("[1,2,3]");
        when(rs.getObject("embedding_dimension")).thenReturn(3);
        doAnswer(invocation -> {
            RowMapper<VectorItem> mapper = invocation.getArgument(2);
            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbcTemplate).query(anyString(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<VectorItem>>any());

        var items = repository.findItems(new VectorProjectionScope(List.of(), Map.of()),
                ProjectionSamplingStrategy.STRATIFIED, 10);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).metadata()).containsEntry("sourceName", "sample").doesNotContainKey("title");
        assertThat(items.get(0).embeddingDimension()).isEqualTo(3);
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
