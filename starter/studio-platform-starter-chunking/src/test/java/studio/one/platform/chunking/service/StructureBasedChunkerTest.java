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
                        block(NormalizedBlockType.HEADING, "Install", "page[1]/h[0]", 0, 0.99d),
                        block(NormalizedBlockType.PARAGRAPH, "Install the engine.", "page[1]/p[1]", 1, 0.90d),
                        block(NormalizedBlockType.PARAGRAPH, "Configure tessdata.", "page[1]/p[2]", 2, 0.80d)))
                .build();

        List<Chunk> chunks = chunker.chunk(document, context(document, 120, 0));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo("Install the engine.\n\nConfigure tessdata.");
        assertThat(chunks.get(0).metadata().section()).isEqualTo("Install");
        assertThat(chunks.get(0).metadata().chunkType()).isEqualTo(studio.one.platform.chunking.core.ChunkType.CHILD);
        assertThat(chunks.get(0).metadata().parentChunkId()).isEqualTo("doc-parent-0");
        assertThat(chunks.get(0).metadata().blockIds()).containsExactly("page[1]/p[1]", "page[1]/p[2]");
        assertThat(chunks.get(0).metadata().confidence()).isCloseTo((0.90d + 0.80d) / 2, org.assertj.core.data.Offset.offset(0.0001d));
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_BLOCK_TYPE, "PARAGRAPH")
                .containsEntry(ChunkMetadata.KEY_SOURCE_FORMAT, "PDF")
                .containsEntry(ChunkMetadata.KEY_CHUNK_TYPE, "child")
                .containsEntry(ChunkMetadata.KEY_HEADING_PATH, "Install")
                .containsEntry(ChunkMetadata.KEY_PARENT_CHUNK_CONTENT, "Install\n\nInstall the engine.\n\nConfigure tessdata.")
                .containsEntry(ChunkMetadata.KEY_PARENT_CHUNK_BLOCK_IDS, List.of("page[1]/h[0]", "page[1]/p[1]", "page[1]/p[2]"))
                .containsEntry(ChunkMetadata.KEY_CHUNK_UNIT, "character")
                .containsEntry(ChunkMetadata.KEY_MAX_SIZE, 120)
                .containsEntry(ChunkMetadata.KEY_OVERLAP, 0)
                .containsEntry(ChunkMetadata.KEY_BLOCK_IDS, List.of("page[1]/p[1]", "page[1]/p[2]"))
                .containsKey(ChunkMetadata.KEY_SOURCE_REFS);
    }

    @Test
    void emitsTableAndOcrBlocksAsStandaloneChunks() {
        StructureBasedChunker chunker = new StructureBasedChunker(120, 10, new RecursiveChunker(120, 10));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .sourceFormat("HTML")
                .blocks(List.of(
                        block(NormalizedBlockType.HEADING, "Metrics", "h1", 0, 0.99d),
                        block(NormalizedBlockType.TABLE, "Name: Alice\nScore: 90", "table[0]", 1, 0.88d),
                        block(NormalizedBlockType.OCR_TEXT, "scanned caption", "image[0]/ocr", 2, 0.77d)))
                .build();

        List<Chunk> chunks = chunker.chunk(document, context(document, 120, 10));

        assertThat(chunks).extracting(Chunk::content)
                .containsExactly("Name: Alice\nScore: 90", "scanned caption");
        assertThat(chunks.get(0).metadata().previousChunkId()).isNull();
        assertThat(chunks.get(0).metadata().nextChunkId()).isEqualTo("doc-1");
        assertThat(chunks.get(1).metadata().previousChunkId()).isEqualTo("doc-0");
        assertThat(chunks.get(1).metadata().nextChunkId()).isNull();
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_CHUNK_TYPE, "table")
                .containsEntry(ChunkMetadata.KEY_BLOCK_TYPE, "TABLE")
                .containsEntry(ChunkMetadata.KEY_SOURCE_REF, "table[0]")
                .containsEntry(ChunkMetadata.KEY_PARENT_CHUNK_CONTENT, "Metrics\n\nName: Alice\nScore: 90\n\nscanned caption");
        assertThat(chunks.get(1).metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_CHUNK_TYPE, "ocr")
                .containsEntry(ChunkMetadata.KEY_BLOCK_TYPE, "OCR_TEXT")
                .containsEntry(ChunkMetadata.KEY_SOURCE_REF, "image[0]/ocr");
    }

    @Test
    void doesNotLinkChildChunksAcrossSectionParents() {
        StructureBasedChunker chunker = new StructureBasedChunker(120, 0, new RecursiveChunker(120, 0));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .blocks(List.of(
                        block(NormalizedBlockType.HEADING, "First", "h1", 0, 0.99d),
                        block(NormalizedBlockType.PARAGRAPH, "First body", "p1", 1, 0.90d),
                        block(NormalizedBlockType.HEADING, "Second", "h2", 2, 0.99d),
                        block(NormalizedBlockType.PARAGRAPH, "Second body", "p2", 3, 0.90d)))
                .build();

        List<Chunk> chunks = chunker.chunk(document, context(document, 120, 0));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).metadata().parentChunkId()).isEqualTo("doc-parent-0");
        assertThat(chunks.get(0).metadata().previousChunkId()).isNull();
        assertThat(chunks.get(0).metadata().nextChunkId()).isNull();
        assertThat(chunks.get(1).metadata().parentChunkId()).isEqualTo("doc-parent-2");
        assertThat(chunks.get(1).metadata().previousChunkId()).isNull();
        assertThat(chunks.get(1).metadata().nextChunkId()).isNull();
    }

    @Test
    void createsParentContextForDocumentsWithoutHeading() {
        StructureBasedChunker chunker = new StructureBasedChunker(120, 0, new RecursiveChunker(120, 0));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .blocks(List.of(
                        block(NormalizedBlockType.PARAGRAPH, "Intro body", "p1", 0, 0.90d),
                        block(NormalizedBlockType.PARAGRAPH, "More body", "p2", 1, 0.80d)))
                .build();

        List<Chunk> chunks = chunker.chunk(document, context(document, 120, 0));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().section()).isEmpty();
        assertThat(chunks.get(0).metadata().parentChunkId()).isEqualTo("doc-parent-0");
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_PARENT_CHUNK_CONTENT, "Intro body\n\nMore body");
    }

    @Test
    void standardBlockIdsAndConfidenceAreStoredOnlyAsMetadataFields() {
        StructureBasedChunker chunker = new StructureBasedChunker(120, 0, new RecursiveChunker(120, 0));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .blocks(List.of(block(NormalizedBlockType.PARAGRAPH, "Body", "p1", 0, 0.90d)))
                .build();

        Chunk chunk = chunker.chunk(document, context(document, 120, 0)).get(0);

        assertThat(chunk.metadata().attributes())
                .doesNotContainKeys(ChunkMetadata.KEY_BLOCK_IDS, ChunkMetadata.KEY_CONFIDENCE);
        assertThat(chunk.metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_BLOCK_IDS, List.of("p1"))
                .containsEntry(ChunkMetadata.KEY_CONFIDENCE, 0.90d);
    }

    @Test
    void tokenUnitUsesDeterministicEstimateWithoutTokenizer() {
        StructureBasedChunker chunker = new StructureBasedChunker(8, 0, new RecursiveChunker(8, 0));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .blocks(List.of(
                        block(NormalizedBlockType.PARAGRAPH, "alpha beta gamma delta", "p1", 0, 0.95d),
                        block(NormalizedBlockType.PARAGRAPH, "epsilon zeta eta theta", "p2", 1, 0.85d)))
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

    private NormalizedBlock block(NormalizedBlockType type, String text, String sourceRef, int order, double confidence) {
        return NormalizedBlock.builder(type, text)
                .id(sourceRef)
                .sourceRef(sourceRef)
                .order(order)
                .blockIds(List.of(sourceRef))
                .confidence(confidence)
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
