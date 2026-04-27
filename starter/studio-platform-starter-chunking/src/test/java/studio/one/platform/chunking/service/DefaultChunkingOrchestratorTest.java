package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.autoconfigure.ChunkingProperties;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;

class DefaultChunkingOrchestratorTest {

    @Test
    void usesConfiguredStrategyWhenContextRequestsDefaults() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setStrategy("fixed-size");
        properties.setMaxSize(5);
        properties.setOverlap(1);
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(10, 0), new RecursiveChunker(10, 0),
                        new StructureBasedChunker(10, 0, new RecursiveChunker(10, 0))));

        var chunks = orchestrator.chunk(ChunkingContext.configuredDefaults("abcdefghij")
                .sourceDocumentId("doc")
                .build());

        assertThat(chunks).extracting(chunk -> chunk.content()).containsExactly("abcde", "efghi", "ij");
    }

    @Test
    void contextStrategyOverridesConfiguredStrategy() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setStrategy("fixed-size");
        properties.setMaxSize(5);
        properties.setOverlap(0);
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(5, 0), new RecursiveChunker(5, 0),
                        new StructureBasedChunker(5, 0, new RecursiveChunker(5, 0))));

        var chunks = orchestrator.chunk(ChunkingContext.configuredDefaults("alpha beta")
                .sourceDocumentId("doc")
                .strategy(ChunkingStrategyType.RECURSIVE)
                .build());

        assertThat(chunks).extracting(chunk -> chunk.content()).containsExactly("alpha", "beta");
    }

    @Test
    void fixedSizeStrategyHonorsTokenUnitApproximately() {
        ChunkingProperties properties = new ChunkingProperties();
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(5, 0), new RecursiveChunker(5, 0),
                        new StructureBasedChunker(5, 0, new RecursiveChunker(5, 0))));

        var chunks = orchestrator.chunk(ChunkingContext.builder("abcdefghijklmnopqrst")
                .sourceDocumentId("doc")
                .strategy(ChunkingStrategyType.FIXED_SIZE)
                .maxSize(2)
                .overlap(0)
                .unit(ChunkUnit.TOKEN)
                .build());

        assertThat(chunks).extracting(chunk -> chunk.content())
                .containsExactly("abcdefgh", "ijklmnop", "qrst");
        assertThat(chunks).extracting(chunk -> chunk.metadata().tokenCount())
                .containsExactly(2, 2, 1);
    }

    @Test
    void recursiveStrategyHonorsTokenUnitApproximately() {
        ChunkingProperties properties = new ChunkingProperties();
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(5, 0), new RecursiveChunker(5, 0),
                        new StructureBasedChunker(5, 0, new RecursiveChunker(5, 0))));

        var chunks = orchestrator.chunk(ChunkingContext.builder("alpha beta gamma delta")
                .sourceDocumentId("doc")
                .strategy(ChunkingStrategyType.RECURSIVE)
                .maxSize(4)
                .overlap(0)
                .unit(ChunkUnit.TOKEN)
                .build());

        assertThat(chunks).extracting(chunk -> chunk.content())
                .containsExactly("alpha beta gamma", "delta");
        assertThat(chunks).extracting(chunk -> chunk.metadata().tokenCount())
                .containsExactly(4, 2);
    }

    @Test
    void rejectsUnsupportedPhaseOneStrategy() {
        ChunkingProperties properties = new ChunkingProperties();
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(10, 0), new RecursiveChunker(10, 0),
                        new StructureBasedChunker(10, 0, new RecursiveChunker(10, 0))));

        assertThatThrownBy(() -> orchestrator.chunk(ChunkingContext.builder("hello")
                .sourceDocumentId("doc")
                .strategy(ChunkingStrategyType.SEMANTIC)
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported pure chunking strategy");
    }

    @Test
    void rejectsMissingChunkerBeanForSupportedStrategy() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setStrategy("fixed-size");
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new RecursiveChunker(10, 0)));

        assertThatThrownBy(() -> orchestrator.chunk(ChunkingContext.configuredDefaults("hello")
                .sourceDocumentId("doc")
                .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chunker bean for strategy FIXED_SIZE is not registered");
    }

    @Test
    void chunksNormalizedDocumentWithStructureStrategy() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setMaxSize(80);
        properties.setOverlap(0);
        RecursiveChunker recursiveChunker = new RecursiveChunker(80, 0);
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(80, 0), recursiveChunker,
                        new StructureBasedChunker(80, 0, recursiveChunker)));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .blocks(List.of(
                        NormalizedBlock.builder(NormalizedBlockType.HEADING, "Title").order(0).build(),
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "Body").order(1).build()))
                .build();

        var chunks = orchestrator.chunk(document);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().strategy()).isEqualTo(ChunkingStrategyType.STRUCTURE_BASED);
    }

    @Test
    void chunksNormalizedDocumentWithExplicitContext() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setMaxSize(80);
        properties.setOverlap(0);
        RecursiveChunker recursiveChunker = new RecursiveChunker(80, 0);
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(80, 0), recursiveChunker,
                        new StructureBasedChunker(80, 0, recursiveChunker)));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .blocks(List.of(
                        NormalizedBlock.builder(NormalizedBlockType.HEADING, "Title").order(0).build(),
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "Body").order(1).build()))
                .build();
        ChunkingContext context = document.toContextBuilder()
                .strategy(ChunkingStrategyType.STRUCTURE_BASED)
                .maxSize(40)
                .overlap(0)
                .build();

        var chunks = orchestrator.chunk(document, context);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().strategy()).isEqualTo(ChunkingStrategyType.STRUCTURE_BASED);
        assertThat(chunks.get(0).metadata().toMap()).containsEntry("maxSize", 40);
    }

    @Test
    void returnsEmptyChunksForBlankNormalizedDocument() {
        ChunkingProperties properties = new ChunkingProperties();
        RecursiveChunker recursiveChunker = new RecursiveChunker(80, 0);
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(80, 0), recursiveChunker,
                        new StructureBasedChunker(80, 0, recursiveChunker)));

        assertThat(orchestrator.chunk(NormalizedDocument.builder("doc").build())).isEmpty();
        assertThat(orchestrator.chunk((NormalizedDocument) null)).isEmpty();
    }
}
