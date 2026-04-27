package studio.one.platform.chunking.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ChunkingContextTest {

    @Test
    void shouldUseRecursiveDefaultsAndSanitizeMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "upload");
        metadata.put("blank", "");
        metadata.put("nullValue", null);

        ChunkingContext context = ChunkingContext.builder("hello world")
                .sourceDocumentId("doc-1")
                .metadata(metadata)
                .build();

        assertThat(context.strategy()).isEqualTo(ChunkingStrategyType.RECURSIVE);
        assertThat(context.maxSize()).isEqualTo(ChunkingContext.DEFAULT_MAX_SIZE);
        assertThat(context.overlap()).isEqualTo(ChunkingContext.DEFAULT_OVERLAP);
        assertThat(context.unit()).isEqualTo(ChunkUnit.CHARACTER);
        assertThat(context.metadata()).containsEntry("source", "upload");
        assertThat(context.metadata()).doesNotContainKeys("blank", "nullValue");
        assertThat(context.metadataValue("source")).contains("upload");
    }

    @Test
    void shouldRejectInvalidSizeAndOverlap() {
        assertThatThrownBy(() -> ChunkingContext.builder("hello").maxSize(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxSize");

        assertThatThrownBy(() -> ChunkingContext.builder("hello").maxSize(10).overlap(10).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void shouldParseStrategyAndUnitValues() {
        assertThat(ChunkingStrategyType.from("fixed-size")).isEqualTo(ChunkingStrategyType.FIXED_SIZE);
        assertThat(ChunkingStrategyType.from(null)).isEqualTo(ChunkingStrategyType.RECURSIVE);
        assertThat(ChunkUnit.from("token")).isEqualTo(ChunkUnit.TOKEN);
        assertThat(ChunkUnit.from(null)).isEqualTo(ChunkUnit.CHARACTER);
    }

    @Test
    void shouldAllowConfiguredDefaultsSignal() {
        ChunkingContext context = ChunkingContext.configuredDefaults("hello")
                .sourceDocumentId("doc")
                .build();

        assertThat(context.strategy()).isNull();
        assertThat(context.maxSize()).isZero();
        assertThat(context.overlap()).isEqualTo(ChunkingContext.USE_CONFIGURED_OVERLAP);
    }
}
