package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.autoconfigure.ChunkingProperties;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.ChunkMetadata;
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
    void rejectsBlockifyWhenDisabled() {
        ChunkingProperties properties = new ChunkingProperties();
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(10, 0), new RecursiveChunker(10, 0),
                        new StructureBasedChunker(10, 0, new RecursiveChunker(10, 0)),
                        new BlockifyChunker(properties.getBlockify(),
                                new StructureBasedChunker(10, 0, new RecursiveChunker(10, 0)),
                                new HeuristicBlockifyGenerator())));

        assertThatThrownBy(() -> orchestrator.chunk(ChunkingContext.builder("hello")
                .sourceDocumentId("doc")
                .strategy(ChunkingStrategyType.BLOCKIFY)
                .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Blockify chunking is disabled");
    }

    @Test
    void usesBlockifyWhenEnabled() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.getBlockify().setEnabled(true);
        RecursiveChunker recursiveChunker = new RecursiveChunker(100, 0);
        StructureBasedChunker structureBasedChunker = new StructureBasedChunker(100, 0, recursiveChunker);
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(100, 0), recursiveChunker, structureBasedChunker,
                        new BlockifyChunker(properties.getBlockify(), structureBasedChunker,
                                new HeuristicBlockifyGenerator())));

        var chunks = orchestrator.chunk(ChunkingContext.builder(
                        "수료 기준은 전체 진도율 80% 이상을 충족하고 최종 평가에서 60점 이상을 취득하는 것입니다. "
                                + "교육 담당자는 두 조건을 모두 확인한 뒤 수료 여부를 확정해야 하며, "
                                + "어느 하나라도 충족하지 못하면 보완 학습 또는 재평가 대상으로 분류합니다.")
                .sourceDocumentId("doc")
                .strategy(ChunkingStrategyType.BLOCKIFY)
                .build());

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().strategy()).isEqualTo(ChunkingStrategyType.BLOCKIFY);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("requestedChunkingStrategy", "blockify")
                .containsEntry("actualChunkingStrategy", "blockify");
    }

    @Test
    void rejectsKnowledgeBlockWhenDisabled() {
        ChunkingProperties properties = new ChunkingProperties();
        RecursiveChunker recursiveChunker = new RecursiveChunker(100, 0);
        StructureBasedChunker structureBasedChunker = new StructureBasedChunker(100, 0, recursiveChunker);
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(100, 0), recursiveChunker, structureBasedChunker,
                        new KnowledgeBlockChunker(properties.getBlockify(), structureBasedChunker,
                                new HeuristicBlockifyGenerator())));

        assertThatThrownBy(() -> orchestrator.chunk(ChunkingContext.builder("hello")
                .sourceDocumentId("doc")
                .strategy(ChunkingStrategyType.KNOWLEDGE_BLOCK)
                .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Knowledge block chunking is disabled");
    }

    @Test
    void usesKnowledgeBlockWhenEnabled() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.getKnowledgeBlock().setEnabled(true);
        RecursiveChunker recursiveChunker = new RecursiveChunker(100, 0);
        StructureBasedChunker structureBasedChunker = new StructureBasedChunker(100, 0, recursiveChunker);
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(100, 0), recursiveChunker, structureBasedChunker,
                        new KnowledgeBlockChunker(properties.getBlockify(), structureBasedChunker,
                                new HeuristicBlockifyGenerator())));

        var chunks = orchestrator.chunk(ChunkingContext.builder(
                        "수료 기준은 전체 진도율 80% 이상을 충족하고 최종 평가에서 60점 이상을 취득하는 것입니다. "
                                + "교육 담당자는 두 조건을 모두 확인한 뒤 수료 여부를 확정해야 하며, "
                                + "어느 하나라도 충족하지 못하면 보완 학습 또는 재평가 대상으로 분류합니다.")
                .sourceDocumentId("doc")
                .strategy(ChunkingStrategyType.KNOWLEDGE_BLOCK)
                .build());

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().strategy()).isEqualTo(ChunkingStrategyType.KNOWLEDGE_BLOCK);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("requestedChunkingStrategy", "knowledge-block")
                .containsEntry("actualChunkingStrategy", "knowledge-block");
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
    void appliesExplicitContextToNormalizedDocument() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setStrategy("recursive");
        properties.setMaxSize(80);
        properties.setOverlap(0);
        RecursiveChunker recursiveChunker = new RecursiveChunker(80, 0);
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(80, 0), recursiveChunker,
                        new StructureBasedChunker(80, 0, recursiveChunker)));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .plainText("abcdefghij")
                .build();

        var chunks = orchestrator.chunk(document, document.toContextBuilder()
                .strategy(ChunkingStrategyType.FIXED_SIZE)
                .maxSize(5)
                .overlap(0)
                .build());

        assertThat(chunks).extracting(chunk -> chunk.content()).containsExactly("abcde", "fghij");
        assertThat(chunks).allMatch(chunk -> chunk.metadata().strategy() == ChunkingStrategyType.FIXED_SIZE);
    }

    @Test
    void appliesConfiguredUnitToNormalizedDocumentChunking() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setUnit("token");
        properties.setMaxSize(5);
        properties.setOverlap(1);
        RecursiveChunker recursiveChunker = new RecursiveChunker(80, 0);
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(80, 0), recursiveChunker,
                        new StructureBasedChunker(80, 0, recursiveChunker)));
        NormalizedDocument document = NormalizedDocument.builder("doc")
                .blocks(List.of(
                        NormalizedBlock.builder(NormalizedBlockType.HEADING, "Title").order(0).build(),
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "한국어 English text").order(1).build()))
                .build();

        var chunks = orchestrator.chunk(document);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_CHUNK_UNIT, "token");
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

    @Test
    void usesTokenBasedChunkerWhenConfiguredUnitIsToken() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setUnit("token");
        properties.setMaxSize(4);
        properties.setOverlap(1);
        TokenBasedChunker tokenBasedChunker = new TokenBasedChunker(
                4,
                1,
                new DefaultTokenizerResolver(properties.getTokenizer(), List.of(new ApproximateTokenizer())));
        DefaultChunkingOrchestrator orchestrator = new DefaultChunkingOrchestrator(
                properties,
                List.of(new FixedSizeChunker(10, 0), new RecursiveChunker(10, 0),
                        new StructureBasedChunker(10, 0, new RecursiveChunker(10, 0))),
                tokenBasedChunker);

        var chunks = orchestrator.chunk(ChunkingContext.configuredDefaults("한국어 English 1234567890 text")
                .sourceDocumentId("doc")
                .metadata(java.util.Map.of("embeddingModel", "unknown-model"))
                .build());

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry(ChunkMetadata.KEY_CHUNK_UNIT, "token")
                .containsEntry(ChunkMetadata.KEY_TOKENIZER_PROVIDER, "approximate")
                .containsEntry(ChunkMetadata.KEY_TOKENIZER_FALLBACK_USED, true);
        assertThat(chunks.get(0).metadata().tokenCount()).isNotNull();
    }
}
