package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.Chunker;
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
    void preservesPageSearchContextMetadataForRagFiltering() {
        StructureBasedChunker chunker = new StructureBasedChunker(500, 0, new RecursiveChunker(500, 0));
        NormalizedBlock pageContext = NormalizedBlock.builder(NormalizedBlockType.PAGE,
                        "Page 1\n개념 다항식\n001 다음식을 전개하시오.\n$x^2+2x+1$")
                .id("doc:page:1:search-context")
                .sourceRef("page[1]/search-context")
                .page(1)
                .order(0)
                .blockIds(List.of("page[1]/block[1]", "page[1]/block[2]"))
                .metadata(Map.of(
                        "searchContextOnly", true,
                        "aggregationType", "PAGE_SEARCH_CONTEXT",
                        "sourceBlockCount", 2,
                        "sourceBlockIds", List.of("page[1]/block[1]", "page[1]/block[2]")))
                .build();
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .sourceFormat("PDF")
                .blocks(List.of(pageContext))
                .build();

        Chunk chunk = chunker.chunk(document, context(document, 500, 0)).get(0);

        assertThat(chunk.content()).contains("개념 다항식", "$x^2+2x+1$");
        assertThat(chunk.metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_SOURCE_REF, "page[1]/search-context")
                .containsEntry(ChunkMetadata.KEY_PAGE, 1)
                .containsEntry("searchContextOnly", true)
                .containsEntry("aggregationType", "PAGE_SEARCH_CONTEXT")
                .containsEntry("sourceBlockCount", 2)
                .containsEntry("sourceBlockIds", List.of("page[1]/block[1]", "page[1]/block[2]"));
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
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_REQUESTED_CHUNKING_STRATEGY, "structure-based")
                .containsEntry(ChunkMetadata.KEY_ACTUAL_CHUNKING_STRATEGY, "recursive")
                .containsEntry(ChunkMetadata.KEY_FALLBACK_STATUS, "APPLIED")
                .containsEntry(ChunkMetadata.KEY_FALLBACK_FROM, "structure-based")
                .containsEntry(ChunkMetadata.KEY_FALLBACK_TO, "recursive")
                .containsEntry(ChunkMetadata.KEY_FALLBACK_REASON, "plain-text-context")
                .containsEntry(ChunkMetadata.KEY_CHUNK_QUALITY_STATUS, "VALID");
    }

    @Test
    void splitsOversizedNormalizedBlockWhileKeepingProvenance() {
        StructureBasedChunker chunker = new StructureBasedChunker(10, 0, new RecursiveChunker(10, 0));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .sourceFormat("PDF")
                .blocks(List.of(block(NormalizedBlockType.PAGE,
                        "alpha beta gamma delta", "page[1]", 0, 0.91d)))
                .build();

        List<Chunk> chunks = chunker.chunk(document, context(document, 10, 0));

        assertThat(chunks).extracting(Chunk::content).containsExactly("alpha beta", "gamma", "delta");
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content()).hasSizeLessThanOrEqualTo(10));
        assertThat(chunks.get(0).metadata().strategy()).isEqualTo(ChunkingStrategyType.STRUCTURE_BASED);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_SOURCE_REF, "page[1]")
                .containsEntry(ChunkMetadata.KEY_BLOCK_TYPE, "PAGE")
                .containsEntry(ChunkMetadata.KEY_BLOCK_IDS, List.of("page[1]"))
                .containsEntry(ChunkMetadata.KEY_CONFIDENCE, 0.91d)
                .containsEntry(ChunkMetadata.KEY_MAX_SIZE, 10);
    }

    @Test
    void dropsOverlapTailWhenItWouldExceedMaxSizeWithNextBlock() {
        StructureBasedChunker chunker = new StructureBasedChunker(10, 4, new RecursiveChunker(10, 4));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .blocks(List.of(
                        block(NormalizedBlockType.PARAGRAPH, "aaaa", "page[1]/block[1]", 0, 0.90d),
                        block(NormalizedBlockType.PARAGRAPH, "bbbb", "page[1]/block[2]", 1, 0.90d),
                        block(NormalizedBlockType.PARAGRAPH, "cccccccc", "page[1]/block[3]", 2, 0.90d)))
                .build();

        List<Chunk> chunks = chunker.chunk(document, context(document, 10, 4));

        assertThat(chunks).extracting(Chunk::content).containsExactly("aaaa\n\nbbbb", "cccccccc");
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.content()).hasSizeLessThanOrEqualTo(10);
            assertThat(chunk.metadata().strategy()).isEqualTo(ChunkingStrategyType.STRUCTURE_BASED);
        });
    }

    @Test
    void missingProvenanceMarksReviewRequiredWithoutFallback() {
        StructureBasedChunker chunker = new StructureBasedChunker(120, 0, new RecursiveChunker(120, 0));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "Body without locator")
                        .order(0)
                        .build()))
                .build();

        Chunk chunk = chunker.chunk(document, context(document, 120, 0)).get(0);

        assertThat(chunk.metadata().strategy()).isEqualTo(ChunkingStrategyType.STRUCTURE_BASED);
        assertThat(chunk.metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_FALLBACK_STATUS, "NOT_REQUIRED")
                .containsEntry(ChunkMetadata.KEY_CHUNK_QUALITY_STATUS, "REVIEW_REQUIRED")
                .containsEntry(ChunkMetadata.KEY_CHUNK_QUALITY_ISSUES, List.of("MISSING_PROVENANCE"));
    }

    @Test
    void sourceQualityWarningsRemainVisibleOnCompletedChunks() {
        StructureBasedChunker chunker = new StructureBasedChunker(120, 0, new RecursiveChunker(120, 0));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .metadata(Map.of(
                        "normalizationStatus", "REVIEW_REQUIRED",
                        "normalizationIssues", List.of("LOW_QUALITY_MATH"),
                        "markdownQualityStatus", "REVIEW_REQUIRED",
                        "markdownQualityIssues", List.of("MATH_VISION_CORRECTION_FAILED")))
                .blocks(List.of(block(NormalizedBlockType.PARAGRAPH,
                        "$A+B=52x'$", "page[8]/block[0]", 0, 0.60d)))
                .build();

        Chunk chunk = chunker.chunk(document, context(document, 120, 0)).get(0);

        assertThat(chunk.metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_CHUNK_QUALITY_STATUS, "REVIEW_REQUIRED")
                .containsEntry(ChunkMetadata.KEY_CHUNK_QUALITY_ISSUES,
                        List.of("LOW_QUALITY_MATH", "MATH_VISION_CORRECTION_FAILED"));
    }

    @Test
    void oversizedStandaloneStructureChunkIsSplitWithoutFullStrategyFallback() {
        StructureBasedChunker chunker = new StructureBasedChunker(10, 0, new RecursiveChunker(10, 0));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .blocks(List.of(block(NormalizedBlockType.TABLE,
                        "alpha beta gamma", "table[1]", 0, 0.90d)))
                .build();

        List<Chunk> chunks = chunker.chunk(document, context(document, 10, 0));

        assertThat(chunks).extracting(Chunk::content).containsExactly("alpha beta", "gamma");
        assertThat(chunks.get(0).metadata().strategy()).isEqualTo(ChunkingStrategyType.STRUCTURE_BASED);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_REQUESTED_CHUNKING_STRATEGY, "structure-based")
                .containsEntry(ChunkMetadata.KEY_ACTUAL_CHUNKING_STRATEGY, "structure-based")
                .containsEntry(ChunkMetadata.KEY_FALLBACK_STATUS, "NOT_REQUIRED")
                .doesNotContainKey(ChunkMetadata.KEY_FALLBACK_REASON);
    }

    @Test
    void recursiveFallbackFailureUsesFixedFallback() {
        Chunker failingRecursive = new Chunker() {
            @Override
            public ChunkingStrategyType strategy() {
                return ChunkingStrategyType.RECURSIVE;
            }

            @Override
            public List<Chunk> chunk(ChunkingContext context) {
                throw new IllegalStateException("recursive unavailable");
            }
        };
        StructureBasedChunker chunker = new StructureBasedChunker(5, 0, failingRecursive, new FixedSizeChunker(5, 0));

        List<Chunk> chunks = chunker.chunk(ChunkingContext.builder("abcdefghi")
                .sourceDocumentId("doc")
                .strategy(ChunkingStrategyType.STRUCTURE_BASED)
                .maxSize(5)
                .overlap(0)
                .build());

        assertThat(chunks).extracting(Chunk::content).containsExactly("abcde", "fghi");
        assertThat(chunks.get(0).metadata().strategy()).isEqualTo(ChunkingStrategyType.FIXED_SIZE);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_REQUESTED_CHUNKING_STRATEGY, "structure-based")
                .containsEntry(ChunkMetadata.KEY_ACTUAL_CHUNKING_STRATEGY, "fixed-size")
                .containsEntry(ChunkMetadata.KEY_FALLBACK_STATUS, "APPLIED")
                .containsEntry(ChunkMetadata.KEY_FALLBACK_FROM, "recursive")
                .containsEntry(ChunkMetadata.KEY_FALLBACK_TO, "fixed-size")
                .containsEntry(ChunkMetadata.KEY_FALLBACK_REASON,
                        "plain-text-context:recursive-fallback-invalid");
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
