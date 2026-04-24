package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;

class StructureBasedChunkerTest {

    @Test
    void keepsHeadingAndParagraphProvenance() {
        StructureBasedChunker chunker = new StructureBasedChunker(120, 0, new RecursiveChunker(120, 0));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .sourceFormat("PDF")
                .blocks(List.of(
                        block(NormalizedBlockType.HEADING, "Install", "page[1]/h[0]", 0),
                        block(NormalizedBlockType.PARAGRAPH, "Install the engine.", "page[1]/p[1]", 1),
                        block(NormalizedBlockType.PARAGRAPH, "Configure tessdata.", "page[1]/p[2]", 2)))
                .build();

        List<Chunk> chunks = chunker.chunk(document, context(document, 120, 0));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).contains("Install", "Install the engine.", "Configure tessdata.");
        assertThat(chunks.get(0).metadata().section()).isEqualTo("Install");
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_BLOCK_TYPE, "HEADING")
                .containsEntry(ChunkMetadata.KEY_SOURCE_FORMAT, "PDF")
                .containsEntry(ChunkMetadata.KEY_HEADING_PATH, "Install")
                .containsEntry(ChunkMetadata.KEY_CHUNK_UNIT, "character")
                .containsEntry(ChunkMetadata.KEY_MAX_SIZE, 120)
                .containsEntry(ChunkMetadata.KEY_OVERLAP, 0)
                .containsKey(ChunkMetadata.KEY_SOURCE_REFS);
    }

    @Test
    void emitsTableAndOcrBlocksAsStandaloneChunks() {
        StructureBasedChunker chunker = new StructureBasedChunker(120, 10, new RecursiveChunker(120, 10));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .sourceFormat("HTML")
                .blocks(List.of(
                        block(NormalizedBlockType.HEADING, "Metrics", "h1", 0),
                        block(NormalizedBlockType.TABLE, "Name: Alice\nScore: 90", "table[0]", 1),
                        block(NormalizedBlockType.OCR_TEXT, "scanned caption", "image[0]/ocr", 2)))
                .build();

        List<Chunk> chunks = chunker.chunk(document, context(document, 120, 10));

        assertThat(chunks).extracting(Chunk::content)
                .containsExactly("Metrics", "Name: Alice\nScore: 90", "scanned caption");
        assertThat(chunks.get(1).metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_BLOCK_TYPE, "TABLE")
                .containsEntry(ChunkMetadata.KEY_SOURCE_REF, "table[0]");
        assertThat(chunks.get(2).metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_BLOCK_TYPE, "OCR_TEXT")
                .containsEntry(ChunkMetadata.KEY_SOURCE_REF, "image[0]/ocr");
    }

    @Test
    void tokenUnitUsesDeterministicEstimateWithoutTokenizer() {
        StructureBasedChunker chunker = new StructureBasedChunker(8, 0, new RecursiveChunker(8, 0));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .blocks(List.of(
                        block(NormalizedBlockType.PARAGRAPH, "alpha beta gamma delta", "p1", 0),
                        block(NormalizedBlockType.PARAGRAPH, "epsilon zeta eta theta", "p2", 1)))
                .build();

        List<Chunk> chunks = chunker.chunk(document, document.toContextBuilder()
                .unit(ChunkUnit.TOKEN)
                .maxSize(8)
                .overlap(0)
                .build());

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_CHUNK_UNIT, "token")
                .containsKey(ChunkMetadata.KEY_TOKEN_ESTIMATE);
    }

    @Test
    void textContextFallsBackToRecursiveChunkingForOversizedPlainText() {
        StructureBasedChunker chunker = new StructureBasedChunker(10, 0, new RecursiveChunker(10, 0));

        List<Chunk> chunks = chunker.chunk(ChunkingContext.builder("alpha beta gamma")
                .sourceDocumentId("doc")
                .strategy(ChunkingStrategyType.STRUCTURE_BASED)
                .maxSize(10)
                .overlap(0)
                .build());

        assertThat(chunks).extracting(Chunk::content).containsExactly("alpha beta", "gamma");
        assertThat(chunks.get(0).metadata().strategy())
                .isEqualTo(ChunkingStrategyType.RECURSIVE);
    }

    private NormalizedBlock block(NormalizedBlockType type, String text, String sourceRef, int order) {
        return NormalizedBlock.builder(type, text)
                .id(sourceRef)
                .sourceRef(sourceRef)
                .order(order)
                .metadata(Map.of("format", "test"))
                .build();
    }

    private ChunkingContext context(NormalizedDocument document, int maxSize, int overlap) {
        return document.toContextBuilder()
                .maxSize(maxSize)
                .overlap(overlap)
                .build();
    }
}
