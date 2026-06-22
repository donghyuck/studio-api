package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.autoconfigure.ChunkingProperties;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;

class BlockifyChunkerTest {

    @Test
    void createsQuestionAnswerChunksWithTraceMetadata() {
        BlockifyChunker chunker = chunker();
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "과정 수료 기준")
                        .id("h1")
                        .order(0)
                        .headingPath("교육과정 운영규정 > 제5장 수료")
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "전체 차시의 80% 이상을 수강하고 최종 평가에서 60점 이상을 취득해야 한다.")
                        .id("p1")
                        .sourceRef("page-22:block-1")
                        .page(22)
                        .order(1)
                        .headingPath("교육과정 운영규정 > 제5장 수료")
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).contains("핵심 질문:", "답변:");
        assertThat(chunks.get(0).metadata().strategy()).isEqualTo(ChunkingStrategyType.BLOCKIFY);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("schemaVersion", "blockify-metadata-v1")
                .containsEntry("requestedChunkingStrategy", "blockify")
                .containsEntry("actualChunkingStrategy", "blockify")
                .containsEntry("validationStatus", "RULE_VALIDATED")
                .containsEntry("promptVersion", "blockify-v1")
                .containsEntry("generatorModel", "heuristic-blockify-v1")
                .containsKey("blockifyFingerprint")
                .containsKey("sourceEvidence");
    }

    @Test
    void fingerprintIsStableForSameInput() {
        BlockifyChunker chunker = chunker();
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "휴가 규정").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "연차 휴가는 사전 신청 후 사용할 수 있다.")
                        .order(1)
                        .build()));

        String first = chunker.chunk(document, context(document)).get(0).metadata().toMap()
                .get("blockifyFingerprint").toString();
        String second = chunker.chunk(document, context(document)).get(0).metadata().toMap()
                .get("blockifyFingerprint").toString();

        assertThat(first).isEqualTo(second).startsWith("sha256:");
    }

    @Test
    void fallsBackForTableSections() {
        BlockifyChunker chunker = chunker();
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "정원 표").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.TABLE, "|과정|정원|\n|A|10|")
                        .id("table-1")
                        .order(1)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.metadata().toMap())
                .containsEntry("requestedChunkingStrategy", "blockify")
                .containsEntry("actualChunkingStrategy", "structure-based")
                .containsEntry("validationStatus", "FALLBACK")
                .containsEntry("fallbackReason", "TABLE_SECTION"));
    }

    @Test
    void fallsBackWhenGeneratorReturnsInvalidBlock() {
        ChunkingProperties properties = properties();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> List.of(new BlockifyBlock("", "", "", List.of(), List.of(), List.of(), 0.0d)));
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "본문 내용입니다.").order(0).build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("validationStatus", "FALLBACK")
                .containsEntry("fallbackReason", "NO_VALID_BLOCKIFY_BLOCK");
    }

    private BlockifyChunker chunker() {
        ChunkingProperties properties = properties();
        return new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                new HeuristicBlockifyGenerator());
    }

    private ChunkingProperties properties() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setMaxSize(200);
        properties.setOverlap(0);
        properties.getBlockify().setEnabled(true);
        return properties;
    }

    private NormalizedDocument document(List<NormalizedBlock> blocks) {
        return NormalizedDocument.builder("mrev-1")
                .sourceFormat("markdown")
                .filename("manual.md")
                .blocks(blocks)
                .metadata(java.util.Map.of(
                        ChunkMetadata.KEY_OBJECT_TYPE, "attachment",
                        ChunkMetadata.KEY_OBJECT_ID, "17"))
                .build();
    }

    private ChunkingContext context(NormalizedDocument document) {
        return document.toContextBuilder()
                .strategy(ChunkingStrategyType.BLOCKIFY)
                .maxSize(200)
                .overlap(0)
                .build();
    }
}
