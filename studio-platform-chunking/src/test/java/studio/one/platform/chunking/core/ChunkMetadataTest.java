package studio.one.platform.chunking.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ChunkMetadataTest {

    @Test
    void shouldOmitNullAndBlankMetadataValues() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("existing", "value");
        attributes.put("blank", " ");
        attributes.put("nullValue", null);
        attributes.put("", "ignored");

        ChunkMetadata metadata = ChunkMetadata.builder(ChunkingStrategyType.RECURSIVE, 2)
                .sourceDocumentId("doc-1")
                .parentId(" ")
                .chunkType(ChunkType.CHILD)
                .parentChunkId("parent-1")
                .previousChunkId("doc-1-1")
                .nextChunkId("doc-1-3")
                .section(null)
                .objectType("attachment")
                .objectId("123")
                .blockIds(java.util.List.of("block-1", " ", "block-2"))
                .confidence(0.92d)
                .attributes(attributes)
                .build();

        assertThat(metadata.attributes()).containsEntry("existing", "value");
        assertThat(metadata.attributes()).doesNotContainKeys("blank", "nullValue", "");
        assertThat(metadata.toMap())
                .containsEntry("existing", "value")
                .containsEntry("sourceDocumentId", "doc-1")
                .containsEntry("chunkType", "child")
                .containsEntry("parentChunkId", "parent-1")
                .containsEntry("previousChunkId", "doc-1-1")
                .containsEntry("nextChunkId", "doc-1-3")
                .containsEntry("chunkOrder", 2)
                .containsEntry("strategy", "recursive")
                .containsEntry("objectType", "attachment")
                .containsEntry("objectId", "123")
                .containsEntry("blockIds", java.util.List.of("block-1", "block-2"))
                .containsEntry("confidence", 0.92d)
                .doesNotContainKeys("parentId", "section");
    }

    @Test
    void shouldPreserveExistingMetadataWhenConvertingToMap() {
        ChunkMetadata metadata = ChunkMetadata.builder(ChunkingStrategyType.FIXED_SIZE, 5)
                .attributes(Map.of(
                        "chunkOrder", 99,
                        "strategy", "legacy",
                        "sourceDocumentId", "existing-doc"))
                .sourceDocumentId("doc-1")
                .build();

        assertThat(metadata.toMap())
                .containsEntry("chunkOrder", 99)
                .containsEntry("strategy", "legacy")
                .containsEntry("sourceDocumentId", "existing-doc");
    }

    @Test
    void shouldRejectNegativeOrder() {
        assertThatThrownBy(() -> ChunkMetadata.builder(ChunkingStrategyType.RECURSIVE, -1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order");
    }
}
