package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.autoconfigure.ChunkingProperties;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;

class KnowledgeBlockChunkerTest {

    @Test
    void adaptsBlockifyMetadataToKnowledgeBlockMetadata() {
        ChunkingProperties properties = new ChunkingProperties();
        KnowledgeBlockChunker chunker = chunker(properties);

        var chunks = chunker.chunk(ChunkingContext.builder(
                        "수료 기준은 전체 진도율 80% 이상이며 최종 평가에서 60점 이상을 취득해야 합니다. "
                                + "두 조건 중 하나라도 충족하지 못하면 보완 학습 또는 재평가 대상으로 분류합니다. "
                                + "교육 담당자는 진도율과 평가 점수를 확인한 뒤 최종 수료 여부를 확정합니다.")
                .sourceDocumentId("doc")
                .strategy(ChunkingStrategyType.KNOWLEDGE_BLOCK)
                .build());

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().strategy()).isEqualTo(ChunkingStrategyType.KNOWLEDGE_BLOCK);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("schemaVersion", "knowledge-block-metadata-v1")
                .containsEntry("knowledgeBlockSchemaVersion", "knowledge-block-v1")
                .containsEntry("requestedChunkingStrategy", "knowledge-block")
                .containsEntry("actualChunkingStrategy", "knowledge-block")
                .containsEntry("knowledgeBlock", true)
                .containsKey("knowledgeBlockFingerprint")
                .containsKey("knowledgeBlockSummary")
                .containsKey("sourceEvidence");
    }

    @Test
    void preservesFallbackMetadataWithKnowledgeBlockAliases() {
        ChunkingProperties properties = new ChunkingProperties();
        KnowledgeBlockChunker chunker = chunker(properties);

        NormalizedDocument document = NormalizedDocument.builder("doc")
                .blocks(List.of(
                        NormalizedBlock.builder(NormalizedBlockType.HEADING, "수료 기준").order(0).build(),
                        NormalizedBlock.builder(NormalizedBlockType.TABLE, "수료 기준 | 진도율 80% 이상")
                                .order(1)
                                .build()))
                .build();
        var chunks = chunker.chunk(document, document.toContextBuilder()
                .strategy(ChunkingStrategyType.KNOWLEDGE_BLOCK)
                .build());

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).metadata().strategy()).isEqualTo(ChunkingStrategyType.KNOWLEDGE_BLOCK);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("requestedChunkingStrategy", "knowledge-block")
                .containsEntry("actualChunkingStrategy", "structure-based")
                .containsEntry("validationStatus", "FALLBACK")
                .containsKey("fallbackReason")
                .containsKey("knowledgeBlockFallbackCount");
    }

    private KnowledgeBlockChunker chunker(ChunkingProperties properties) {
        RecursiveChunker recursiveChunker = new RecursiveChunker(1000, 0);
        StructureBasedChunker structureBasedChunker = new StructureBasedChunker(1000, 0, recursiveChunker);
        return new KnowledgeBlockChunker(properties.getBlockify(), structureBasedChunker,
                request -> new HeuristicBlockifyGenerator().generate(request).stream().limit(1).toList());
    }
}
