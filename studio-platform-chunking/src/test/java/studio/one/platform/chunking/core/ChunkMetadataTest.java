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
                .section(null)
                .objectType("attachment")
                .objectId("123")
                .attributes(attributes)
                .build();

        assertThat(metadata.attributes()).containsEntry("existing", "value");
        assertThat(metadata.attributes()).doesNotContainKeys("blank", "nullValue", "");
        assertThat(metadata.toMap())
                .containsEntry("existing", "value")
                .containsEntry("sourceDocumentId", "doc-1")
                .containsEntry("chunkOrder", 2)
                .containsEntry("strategy", "recursive")
                .containsEntry("objectType", "attachment")
                .containsEntry("objectId", "123")
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
