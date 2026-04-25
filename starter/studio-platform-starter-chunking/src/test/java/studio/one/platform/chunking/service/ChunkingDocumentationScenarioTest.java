package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.autoconfigure.ChunkingProperties;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkContextExpansion;
import studio.one.platform.chunking.core.ChunkContextExpansionRequest;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;

class ChunkingDocumentationScenarioTest {

    @Test
    void parentChildReadmeScenarioProducesChildChunkWithRecoverableParentContext() {
        DefaultChunkingOrchestrator orchestrator = orchestrator();
        NormalizedDocument document = NormalizedDocument.builder("doc-1")
                .sourceFormat("PDF")
                .blocks(List.of(
                        NormalizedBlock.builder(NormalizedBlockType.HEADING, "Install")
                                .id("page[1]/h[0]")
                                .order(0)
                                .headingPath("Install")
                                .blockIds(List.of("page[1]/h[0]"))
                                .build(),
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "Install the engine.")
                                .id("page[1]/p[1]")
                                .order(1)
                                .blockIds(List.of("page[1]/p[1]"))
                                .build(),
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "Configure tessdata.")
                                .id("page[1]/p[2]")
                                .order(2)
                                .blockIds(List.of("page[1]/p[2]"))
                                .build()))
                .build();

        List<Chunk> chunks = orchestrator.chunk(document);

        assertThat(chunks).hasSize(1);
        Chunk chunk = chunks.get(0);
        assertThat(chunk.content()).isEqualTo("Install the engine.\n\nConfigure tessdata.");
        assertThat(chunk.metadata().chunkType()).isEqualTo(ChunkType.CHILD);
        assertThat(chunk.metadata().parentChunkId()).isEqualTo("doc-1-parent-0");
        assertThat(chunk.metadata().previousChunkId()).isNull();
        assertThat(chunk.metadata().nextChunkId()).isNull();
        assertThat(chunk.metadata().blockIds()).containsExactly("page[1]/p[1]", "page[1]/p[2]");
        assertThat(chunk.metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_PARENT_CHUNK_CONTENT,
                        "Install\n\nInstall the engine.\n\nConfigure tessdata.")
                .containsEntry(ChunkMetadata.KEY_HEADING_PATH, "Install")
                .containsEntry(ChunkMetadata.KEY_SOURCE_FORMAT, "PDF");
    }

    @Test
    void contextExpansionReadmeScenarioRestoresAnswerContextWithoutRetrievalSideEffects() {
        Chunk seed = chunk("doc-1-0", "Install the engine.", 0, "doc-1-parent-0", null, "doc-1-1",
                "Install\n\nInstall the engine.\n\nConfigure tessdata.");
        Chunk next = chunk("doc-1-1", "Configure tessdata.", 1, "doc-1-parent-0", "doc-1-0", null,
                "Install\n\nInstall the engine.\n\nConfigure tessdata.");

        ChunkContextExpansionRequest request = ChunkContextExpansionRequest.builder(seed)
                .availableChunks(List.of(seed, next))
                .previousWindow(1)
                .nextWindow(1)
                .includeParentContent(true)
                .build();

        ChunkContextExpansion window = new WindowChunkContextExpander().expand(request);
        ChunkContextExpansion parent = new ParentChildChunkContextExpander().expand(request);

        assertThat(window.content()).isEqualTo("Install the engine.\n\nConfigure tessdata.");
        assertThat(parent.content()).isEqualTo("Install\n\nInstall the engine.\n\nConfigure tessdata.");
        assertThat(parent.metadata()).containsEntry(ChunkMetadata.KEY_PARENT_CHUNK_ID, "doc-1-parent-0");
    }

    @Test
    void textCompatibilityPathStillUsesConfiguredTextStrategy() {
        StructureBasedChunker chunker = new StructureBasedChunker(10, 0, new RecursiveChunker(10, 0));

        List<Chunk> chunks = chunker.chunk(ChunkingContext.builder("alpha beta gamma")
                .sourceDocumentId("doc")
                .strategy(ChunkingStrategyType.STRUCTURE_BASED)
                .maxSize(10)
                .overlap(0)
                .build());

        assertThat(chunks).extracting(Chunk::content).containsExactly("alpha beta", "gamma");
        assertThat(chunks).extracting(chunk -> chunk.metadata().strategy())
                .containsOnly(ChunkingStrategyType.RECURSIVE);
    }

    private DefaultChunkingOrchestrator orchestrator() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setStrategy("structure-based");
        properties.setMaxSize(120);
        properties.setOverlap(0);
        RecursiveChunker recursiveChunker = new RecursiveChunker(120, 0);
        return new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(120, 0), recursiveChunker,
                        new StructureBasedChunker(120, 0, recursiveChunker)));
    }

    private Chunk chunk(
            String id,
            String content,
            int order,
            String parentChunkId,
            String previousChunkId,
            String nextChunkId,
            String parentContent) {
        ChunkMetadata metadata = ChunkMetadata.builder(ChunkingStrategyType.STRUCTURE_BASED, order)
                .sourceDocumentId("doc-1")
                .chunkType(ChunkType.CHILD)
                .parentChunkId(parentChunkId)
                .previousChunkId(previousChunkId)
                .nextChunkId(nextChunkId)
                .section("Install")
                .attribute(ChunkMetadata.KEY_PARENT_CHUNK_CONTENT, parentContent)
                .build();
        return Chunk.of(id, content, metadata);
    }
}
