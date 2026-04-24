package studio.one.platform.chunking.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class NormalizedDocumentTest {

    @Test
    void keepsParserNeutralBlocksSortedByOrder() {
        NormalizedBlock second = NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "second")
                .sourceRef("page[1]/p[1]")
                .order(2)
                .metadata(Map.of("blank", " "))
                .build();
        NormalizedBlock first = NormalizedBlock.builder(NormalizedBlockType.HEADING, "first")
                .sourceRef("page[1]/h[0]")
                .order(1)
                .page(1)
                .build();

        NormalizedDocument document = NormalizedDocument.builder("doc")
                .sourceFormat("PDF")
                .blocks(List.of(second, first))
                .build();

        assertThat(document.blocks()).extracting(NormalizedBlock::text).containsExactly("first", "second");
        assertThat(document.chunkableText()).isEqualTo("first\n\nsecond");
        assertThat(document.blocks().get(0).effectiveSourceRef()).isEqualTo("page[1]/h[0]");
        assertThat(document.blocks().get(0).blockIds()).containsExactly("page[1]/h[0]");
    }

    @Test
    void exposesStructuredMetadataKeysWithoutChangingLegacyMapContract() {
        ChunkMetadata metadata = ChunkMetadata.builder(ChunkingStrategyType.STRUCTURE_BASED, 1)
                .sourceDocumentId("doc")
                .attribute(ChunkMetadata.KEY_SOURCE_REF, "page[1]/table[0]")
                .attribute(ChunkMetadata.KEY_BLOCK_TYPE, "TABLE")
                .attribute(ChunkMetadata.KEY_TOKEN_ESTIMATE, 12)
                .build();

        assertThat(metadata.toMap())
                .containsEntry(ChunkMetadata.KEY_CHUNK_ORDER, 1)
                .containsEntry(ChunkMetadata.KEY_STRATEGY, "structure-based")
                .containsEntry(ChunkMetadata.KEY_SOURCE_REF, "page[1]/table[0]")
                .containsEntry(ChunkMetadata.KEY_BLOCK_TYPE, "TABLE")
                .containsEntry(ChunkMetadata.KEY_TOKEN_ESTIMATE, 12);
    }
}
