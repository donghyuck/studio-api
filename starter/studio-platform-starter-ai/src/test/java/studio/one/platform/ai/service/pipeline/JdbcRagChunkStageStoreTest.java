package studio.one.platform.ai.service.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcRagChunkStageStoreTest {

    @Test
    void writesChunksInConfiguredBatches() {
        NamedParameterJdbcTemplate template = mock(NamedParameterJdbcTemplate.class);
        JdbcRagChunkStageStore store = new JdbcRagChunkStageStore(template, new ObjectMapper(), 2, null);

        store.replace("attachment", "5", "mdoc-5", chunks(5));

        ArgumentCaptor<MapSqlParameterSource[]> batches = ArgumentCaptor.forClass(MapSqlParameterSource[].class);
        verify(template, times(3)).batchUpdate(anyString(), batches.capture());
        assertThat(batches.getAllValues()).extracting(batch -> batch.length).containsExactly(2, 2, 1);
    }

    @Test
    void boundsUnsafeBatchSizes() {
        NamedParameterJdbcTemplate template = mock(NamedParameterJdbcTemplate.class);
        JdbcRagChunkStageStore store = new JdbcRagChunkStageStore(template, new ObjectMapper(), 1_000, null);

        store.replace("attachment", "5", "mdoc-5", chunks(201));

        ArgumentCaptor<MapSqlParameterSource[]> batches = ArgumentCaptor.forClass(MapSqlParameterSource[].class);
        verify(template, times(2)).batchUpdate(anyString(), batches.capture());
        assertThat(batches.getAllValues()).extracting(batch -> batch.length).containsExactly(200, 1);
    }

    @Test
    void usesSafeDefaultForNonPositiveBatchSize() {
        NamedParameterJdbcTemplate template = mock(NamedParameterJdbcTemplate.class);
        JdbcRagChunkStageStore store = new JdbcRagChunkStageStore(template, new ObjectMapper(), 0, null);

        store.replace("attachment", "5", "mdoc-5", chunks(26));

        ArgumentCaptor<MapSqlParameterSource[]> batches = ArgumentCaptor.forClass(MapSqlParameterSource[].class);
        verify(template, times(2)).batchUpdate(anyString(), batches.capture());
        assertThat(batches.getAllValues()).extracting(batch -> batch.length).containsExactly(25, 1);
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void readsBoundedChunksWithoutDocumentWideDiagnostics() {
        NamedParameterJdbcTemplate template = mock(NamedParameterJdbcTemplate.class);
        when(template.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        JdbcRagChunkStageStore store = new JdbcRagChunkStageStore(template, new ObjectMapper());

        store.findBatchByObject("attachment", "5", "mdoc-5", 24, 25);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(template).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertThat(sql.getValue())
                .contains("- 'pdfExtractionParts'")
                .contains("- 'pageQuality'")
                .contains("- 'parentChunkContent'")
                .contains("- 'parentChunkBlockIds'")
                .contains("- 'parentChunkSourceRefs'")
                .contains("chunk_index > :afterChunkIndex")
                .contains("LIMIT :limit");
        assertThat(params.getValue().getValue("afterChunkIndex")).isEqualTo(24);
        assertThat(params.getValue().getValue("limit")).isEqualTo(25);
    }

    private List<RagChunkStage> chunks(int count) {
        List<RagChunkStage> chunks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            chunks.add(new RagChunkStage(
                    "attachment", "5", "mdoc-5", index, "chunk-" + index,
                    "content-" + index, Map.of("page", index + 1), Instant.now()));
        }
        return chunks;
    }
}
