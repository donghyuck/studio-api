package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                                "전체 차시의 80% 이상을 수강하고 최종 평가에서 60점 이상을 취득해야 한다. "
                                        + "두 조건을 모두 충족하지 못한 교육생은 수료 처리하지 않으며, "
                                        + "관리자는 수강 이력과 평가 점수를 함께 확인하여 수료 여부를 판정한다.")
                        .id("p1")
                        .sourceRef("page-22:block-1")
                        .page(22)
                        .order(1)
                        .headingPath("교육과정 운영규정 > 제5장 수료")
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content())
                .contains("핵심 질문:", "답변:", "핵심 원문 Evidence:", "전체 차시의 80% 이상")
                .doesNotContain("답변:\n교육과정 운영규정 > 제5장 수료");
        assertThat(chunks.get(0).metadata().strategy()).isEqualTo(ChunkingStrategyType.BLOCKIFY);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("schemaVersion", "blockify-metadata-v1")
                .containsEntry("requestedChunkingStrategy", "blockify")
                .containsEntry("actualChunkingStrategy", "blockify")
                .containsEntry("chunkType", "ideaBlock")
                .containsEntry("validationStatus", "RULE_VALIDATED")
                .containsEntry("promptVersion", "blockify-v1")
                .containsEntry("generatorModel", "heuristic-blockify-v1")
                .containsEntry("criticalQuestion", chunks.get(0).metadata().toMap().get("question"))
                .containsEntry("trustedAnswer", chunks.get(0).metadata().toMap().get("answer"))
                .containsEntry("ideaBlockChunkCount", 1)
                .containsEntry("ideaBlockCount", 1)
                .containsEntry("ideaBlockFallbackCount", 0)
                .containsEntry("ideaBlockSourceBlockTargetCount", 1)
                .containsEntry("ideaBlockSourceBlockCoveredCount", 1)
                .containsEntry("ideaBlockSourceBlockCoverage", 1.0d)
                .containsKey("blockifyFingerprint")
                .containsKey("ideaBlockFingerprint")
                .containsKey("sourceBlockRange")
                .containsKey("sourceEvidence");
    }

    @Test
    void detectsPolicyDocumentAndStoresPolicyTypedFields() {
        ChunkingProperties properties = properties();
        properties.getBlockify().setMinAnswerChars(20);
        properties.getBlockify().setMinChunkChars(30);
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                new HeuristicBlockifyGenerator());
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "제12조(연차휴가)")
                        .id("h1")
                        .order(0)
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원은 연차휴가를 사용하려는 경우 사용 기간과 사유를 명확히 기재하여 사전에 신청하고 승인을 받아야 한다. "
                                        + "다만 회사는 업무상 필요한 경우 업무 운영에 중대한 지장이 없도록 휴가 시기를 변경할 수 있다.")
                        .id("p1")
                        .order(1)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.metadata().toMap())
                .containsEntry("detectedDocumentType", "policy")
                .containsEntry("blockifyProfile", "policy-v1")
                .containsEntry("ideaBlockSchemaVersion", "blockify-policy-v1")
                .containsEntry("actualChunkingStrategy", "blockify")
                .containsEntry("chunkType", "ideaBlock")
                .containsEntry("articleNo", "제12조")
                .containsEntry("ruleName", "연차휴가"));
        assertThat(chunks).anySatisfy(chunk -> assertThat(chunk.metadata().toMap()).containsKey("obligation"));
        assertThat(chunks).anySatisfy(chunk -> assertThat(chunk.metadata().toMap()).containsKey("exception"));
    }

    @Test
    void detectsNarrativeDocumentAndStoresNarrativeTypedFields() {
        BlockifyChunker chunker = chunker();
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "CHAPTER ONE")
                        .id("h1")
                        .order(0)
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "Holden Caulfield explains that he was at Pencey Prep and that he was flunking four subjects. "
                                        + "\"They kicked me out,\" he says, because he was not supposed to come back after Christmas vacation.")
                        .id("p1")
                        .order(1)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("detectedDocumentType", "narrative")
                .containsEntry("blockifyProfile", "narrative-v1")
                .containsEntry("ideaBlockSchemaVersion", "blockify-narrative-v1")
                .containsEntry("character", "Holden Caulfield")
                .containsEntry("eventType", "school-expulsion")
                .containsEntry("location", "Pencey Prep")
                .containsKey("event")
                .containsKey("quote");
    }

    @Test
    void narrativeDetectionDoesNotTreatTechnicalKeywordsAsSubstrings() {
        BlockifyDocumentTypeClassifier classifier = new BlockifyDocumentTypeClassifier();
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "CHAPTER ONE")
                        .id("h1")
                        .order(0)
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "Holden said that the capital letters on the advertisement made him unhappy, "
                                        + "and he asked why every response from the school sounded phony. "
                                        + "\"I never even saw a horse near Pencey,\" he said, while describing the scene.")
                        .id("p1")
                        .order(1)
                        .build()));

        BlockifyDocumentTypeClassification classification = classifier.classify(document, context(document));

        assertThat(classification.effectiveType()).isEqualTo(BlockifyDocumentType.NARRATIVE);
    }

    @Test
    void blockifyDocumentTypeOverrideWinsOverAutomaticClassification() {
        BlockifyChunker chunker = chunker();
        NormalizedDocument document = NormalizedDocument.builder("mrev-override")
                .sourceFormat("markdown")
                .filename("manual.md")
                .metadata(Map.of("blockifyDocumentType", "narrative"))
                .blocks(List.of(
                        NormalizedBlock.builder(NormalizedBlockType.HEADING, "제1조(목적)")
                                .order(0)
                                .build(),
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                        "이 규정은 회사의 복무 기준과 휴가 신청 절차를 정하는 것을 목적으로 한다.")
                                .order(1)
                                .build()))
                .build();

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("requestedDocumentType", "narrative")
                .containsEntry("detectedDocumentType", "narrative")
                .containsEntry("blockifyProfile", "narrative-v1")
                .containsEntry("ideaBlockSchemaVersion", "blockify-narrative-v1");
    }

    @Test
    void createsMultipleIdeaBlocksBySourceBlock() {
        BlockifyChunker chunker = chunker();
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "제10조(휴가)")
                        .id("h1")
                        .order(0)
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 하며, 승인된 기간에 휴가를 사용할 수 있다. "
                                        + "휴가 신청에는 사용 기간과 사유를 명확히 기재하고 승인 결과를 확인해야 한다.")
                        .id("p1")
                        .order(1)
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "부서장은 업무 운영에 중대한 지장이 있는 경우 사용 시기의 변경을 요청할 수 있으며, 변경 사유를 사원에게 안내해야 한다. "
                                        + "변경 요청은 업무 공백을 줄이기 위한 범위에서 이루어져야 한다.")
                        .id("p2")
                        .order(2)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.metadata().toMap())
                .containsEntry("chunkType", "ideaBlock")
                .containsEntry("actualChunkingStrategy", "blockify")
                .containsKey("sourceBlockRange")
                .containsKey("sourceEvidence"));
        assertThat(chunks)
                .extracting(chunk -> (Integer) ((Map<?, ?>) chunk.metadata().toMap().get("sourceBlockRange")).get("start"))
                .containsExactly(1, 2);
    }

    @Test
    void keepsDuplicateIdeaBlocksWhenSourceBlocksDiffer() {
        ChunkingProperties properties = properties();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> List.of(new BlockifyBlock(
                        "휴가 신청",
                        "휴가 신청 기준은 무엇인가?",
                        "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 한다. 사용 기간과 사유를 명확히 기재해야 하며 승인 결과를 확인한 뒤 승인된 기간에 휴가를 사용해야 한다.",
                        List.of("휴가", "신청"),
                        List.of("휴가"),
                        List.of(new BlockifySourceEvidence(
                                "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 한다. 사용 기간과 사유를 명확히 기재해야 하며 승인 결과를 확인한 뒤 승인된 기간에 휴가를 사용해야 한다.",
                                request.blocks().get(request.blocks().size() - 1).order(),
                                null, null, null, null, List.of("휴가"),
                                request.sectionId(), List.of())),
                        0.9d)));
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "제10조(휴가)").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 한다. 사용 기간과 사유를 명확히 기재해야 하며 승인 결과를 확인한 뒤 승인된 기간에 휴가를 사용해야 한다.")
                        .order(1)
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 한다. 사용 기간과 사유를 명확히 기재해야 하며 승인 결과를 확인한 뒤 승인된 기간에 휴가를 사용해야 한다.")
                        .order(2)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.metadata().toMap())
                .containsEntry("distilled", true)
                .containsEntry("ideaBlockDistillationEnabled", true)
                .containsEntry("ideaBlockDistillationStrategy",
                        "normalized-question-answer-dedup+lexical-similarity-candidates")
                .containsEntry("ideaBlockDistillationDroppedDuplicateCount", 0)
                .containsEntry("ideaBlockDistillationGroupSize", 1));
    }

    @Test
    void marksSimilarityMergeCandidatesWithoutMergingWhenFactsMatch() {
        ChunkingProperties properties = properties();
        properties.getBlockify().setDistillationSimilarityThreshold(0.35d);
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> {
                    NormalizedBlock sourceBlock = request.blocks().get(request.blocks().size() - 1);
                    int order = sourceBlock.order();
                    String question = order == 1
                            ? "연차휴가 사전 신청 승인 기준은 무엇인가?"
                            : "연차휴가 부서장 신청 승인 기준은 무엇인가?";
                    return List.of(new BlockifyBlock(
                            "휴가 승인",
                            question,
                            sourceBlock.text(),
                            List.of("연차휴가", "승인"),
                            List.of("휴가"),
                            List.of(new BlockifySourceEvidence(
                                    sourceBlock.text(),
                                    sourceBlock.order(),
                                    null, null, null, null, List.of("휴가"),
                                    request.sectionId(), List.of())),
                            0.9d));
                });
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "제10조(휴가)").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 하며, 승인 결과를 확인한 뒤 승인된 기간에 휴가를 사용해야 한다. 신청 내역은 근태 관리 기준에 따라 확인한다.")
                        .order(1)
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원은 연차휴가를 사용하려는 경우 부서장에게 신청하여 승인을 받아야 하며, 승인 결과를 확인한 뒤 승인된 기간에 휴가를 사용해야 한다. 신청 내역은 근태 관리 기준에 따라 확인한다.")
                        .order(2)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.metadata().toMap())
                .containsEntry("ideaBlockMergeCandidate", true)
                .containsEntry("ideaBlockMergePolicy", "candidate-only")
                .containsEntry("ideaBlockSimilarityClusterSize", 2)
                .containsKey("ideaBlockSimilarityClusterId")
                .containsKey("ideaBlockSimilarityMaxScore"));
    }

    @Test
    void doesNotMarkSimilarityMergeCandidatesWhenFactTokensDiffer() {
        ChunkingProperties properties = properties();
        properties.getBlockify().setDistillationSimilarityThreshold(0.1d);
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> {
                    int order = request.blocks().get(request.blocks().size() - 1).order();
                    String days = order == 1 ? "3일" : "5일";
                    String source = request.blocks().get(request.blocks().size() - 1).text();
                    return List.of(new BlockifyBlock(
                            "휴가 신청 기한",
                            "연차휴가 신청 기한은 언제인가?",
                            source,
                            List.of("연차휴가", "신청"),
                            List.of("휴가"),
                            List.of(new BlockifySourceEvidence(source, order, null, null, null, null,
                                    List.of("휴가"), request.sectionId(), List.of())),
                            0.9d));
                });
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "제10조(휴가)").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원은 연차휴가를 사용하려는 경우 3일 전에 신청하여 승인을 받아야 하며, 승인 결과를 확인한 뒤 승인된 기간에 휴가를 사용해야 한다.")
                        .order(1)
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원은 연차휴가를 사용하려는 경우 5일 전에 신청하여 승인을 받아야 하며, 승인 결과를 확인한 뒤 승인된 기간에 휴가를 사용해야 한다.")
                        .order(2)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.metadata().toMap())
                .doesNotContainKey("ideaBlockMergeCandidate")
                .doesNotContainKey("ideaBlockSimilarityClusterId"));
    }

    @Test
    void doesNotMarkSimilarityMergeCandidatesWhenEntityConflicts() {
        ChunkingProperties properties = properties();
        properties.getBlockify().setDistillationSimilarityThreshold(0.1d);
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> {
                    NormalizedBlock sourceBlock = request.blocks().get(request.blocks().size() - 1);
                    int order = sourceBlock.order();
                    String entity = order == 1 ? "연차휴가" : "징계";
                    return List.of(new BlockifyBlock(
                            entity,
                            entity,
                            entity + " 승인 기준은 무엇인가?",
                            sourceBlock.text(),
                            List.of(entity, "승인"),
                            List.of("규정"),
                            entity,
                            "policy",
                            List.of(new BlockifySourceEvidence(sourceBlock.text(), order, null, null, null, null,
                                    List.of(entity), request.sectionId(), List.of())),
                            null,
                            request.sectionId(),
                            0.9d));
                });
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "제10조(승인)").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 하며, 승인 결과를 확인한 뒤 승인된 기간에 휴가를 사용해야 한다.")
                        .order(1)
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "회사는 징계 사유가 발생한 경우 관련 절차에 따라 사실 관계를 확인하고 승인된 기준에 따라 조치한다. "
                                        + "징계 여부와 수위는 규정에 정한 절차와 승인 기준을 기준으로 결정하며 처리 결과를 기록한다.")
                        .order(2)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.metadata().toMap())
                .doesNotContainKey("ideaBlockMergeCandidate")
                .doesNotContainKey("ideaBlockSimilarityClusterId"));
    }

    @Test
    void fallsBackWhenTrustedAnswerContainsUngroundedFactToken() {
        ChunkingProperties properties = properties();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> List.of(new BlockifyBlock(
                        "연차휴가",
                        "연차휴가 사용 기준은 무엇인가?",
                        "사원은 연차휴가를 15일 사용할 수 있다.",
                        List.of("연차휴가"),
                        List.of(),
                        List.of(new BlockifySourceEvidence(
                                "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 하며 사용 기간을 확인해야 한다.",
                                1, null, null, null, null, List.of("연차휴가"), "section-1", List.of("p1"))),
                        0.8d)));
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "연차휴가").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 하며 사용 기간을 확인해야 한다.")
                        .id("p1")
                        .order(1)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("validationStatus", "FALLBACK")
                .containsEntry("fallbackReason", "TRUSTED_ANSWER_FACT_MISMATCH")
                .containsEntry("ideaBlockChunkCount", 1)
                .containsEntry("ideaBlockCount", 0)
                .containsEntry("ideaBlockFallbackCount", 1)
                .containsEntry("ideaBlockSourceBlockCoverage", 1.0d);
    }

    @Test
    void fingerprintIsStableForSameInput() {
        BlockifyChunker chunker = chunker();
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "휴가 규정").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "연차 휴가는 사전 신청 후 사용할 수 있으며, 부서장은 업무 운영에 중대한 지장이 없는지 확인한 뒤 승인 여부를 결정한다. "
                                        + "사원은 승인된 기간에 휴가를 사용하고 변경이 필요한 경우 다시 신청해야 한다.")
                        .order(1)
                        .build()));

        String first = chunker.chunk(document, context(document)).get(0).metadata().toMap()
                .get("blockifyFingerprint").toString();
        String second = chunker.chunk(document, context(document)).get(0).metadata().toMap()
                .get("blockifyFingerprint").toString();

        assertThat(first).isEqualTo(second).startsWith("sha256:");
    }

    @Test
    void usesConfiguredBlockifyLlmModelInGenerationMetadata() {
        ChunkingProperties properties = properties();
        properties.getBlockify().setLlmProvider("google-ai-gemini");
        properties.getBlockify().setLlmModel("gemini-2.5-flash");
        List<BlockifyGenerationRequest> requests = new ArrayList<>();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> {
                    requests.add(request);
                    return new HeuristicBlockifyGenerator().generate(request);
                });
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "복무 기준").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원은 근무시간과 휴게시간을 준수해야 하며, 회사는 취업규칙에 따라 복무 기준을 안내한다. "
                                        + "관리자는 근태 기록을 확인하고 필요한 경우 사원에게 보완 조치를 요청할 수 있다.")
                        .order(1)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).llmProvider()).isEqualTo("google-ai-gemini");
        assertThat(requests.get(0).llmModel()).isEqualTo("gemini-2.5-flash");
        assertThat(requests.get(0).generatorModel()).isEqualTo("gemini-2.5-flash");
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("generatorProvider", "google-ai-gemini")
                .containsEntry("generatorLlmModel", "gemini-2.5-flash")
                .containsEntry("generatorModel", "gemini-2.5-flash");
    }

    @Test
    void requestBlockifyLlmModelOverridesConfiguredModel() {
        ChunkingProperties properties = properties();
        properties.getBlockify().setLlmProvider("server-provider");
        properties.getBlockify().setLlmModel("server-model");
        List<BlockifyGenerationRequest> requests = new ArrayList<>();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> {
                    requests.add(request);
                    return new HeuristicBlockifyGenerator().generate(request);
                });
        NormalizedDocument document = documentWithBlockifyLlm(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "휴가 기준").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 한다. "
                                        + "부서장은 업무 운영에 중대한 지장이 없는지 확인한 뒤 승인 여부를 결정한다.")
                        .order(1)
                        .build()), "client-provider", "client-model");

        var chunks = chunker.chunk(document, context(document));

        assertThat(requests.get(0).llmProvider()).isEqualTo("client-provider");
        assertThat(requests.get(0).llmModel()).isEqualTo("client-model");
        assertThat(requests.get(0).generatorModel()).isEqualTo("client-model");
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("generatorProvider", "client-provider")
                .containsEntry("generatorLlmModel", "client-model")
                .containsEntry("generatorModel", "client-model");
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
                .containsEntry("fallbackReason", "ANSWER_HAS_NO_BODY");
    }

    @Test
    void skipsHeadingOnlySections() {
        BlockifyChunker chunker = chunker();
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "목 차")
                        .order(0)
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "제 1 장 총칙")
                        .order(1)
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "제1조(목적)")
                        .order(2)
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "이 규칙은 회사의 복무 기준을 정하는 것을 목적으로 한다. "
                                        + "사원은 근무시간, 휴게, 휴일, 휴가, 징계 및 퇴직 등 복무 전반에 관한 기준을 준수해야 한다.")
                        .order(3)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content())
                .contains("복무 기준")
                .doesNotContain("제 1 장 총칙");
    }

    @Test
    void contextTextIsSplitByMarkdownHeadingsAndSkipsTocSection() {
        ChunkingProperties properties = properties();
        List<BlockifyGenerationRequest> requests = new ArrayList<>();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> {
                    requests.add(request);
                    return new HeuristicBlockifyGenerator().generate(request);
                });
        String markdown = """
                # 목 차 {#목-차 .TOC-Heading}

                [제 1 장 총칙 [- 6 -](#제-1-장-총칙)](#제-1-장-총칙)
                [제1조(목적) [- 6 -](#_Toc220912924)](#_Toc220912924)
                [제2조(적용범위) [- 6 -](#제2조적용범위)](#제2조적용범위)
                [제3조(사원의 정의) [- 6 -](#_Toc220912926)](#_Toc220912926)

                # 제 1 장 총칙

                []{#_Toc220912924 .anchor}**제1조(목적)** 이 규칙은 회사 사원의 채용, 복무 및 근로조건 등에 관한 사항을 정함을 목적으로 한다.

                ## 제2조(적용범위)

                이 규칙은 회사에 근무하는 모든 사원에게 적용하며, 다른 규정에 특별히 정한 경우를 제외하고 이 규칙에서 정한 기준을 따른다.
                """;
        ChunkingContext context = ChunkingContext.builder(markdown)
                .sourceDocumentId("mrev-plain")
                .contentType("markdown")
                .filename("manual.md")
                .objectType("attachment")
                .objectId("18")
                .strategy(ChunkingStrategyType.BLOCKIFY)
                .maxSize(200)
                .overlap(0)
                .build();

        var chunks = chunker.chunk(context);

        assertThat(requests)
                .extracting(BlockifyGenerationRequest::headingPath)
                .noneMatch(heading -> heading != null && heading.contains("목 차"));
        assertThat(requests)
                .extracting(BlockifyGenerationRequest::headingPath)
                .contains("제1조(목적)", "제2조(적용범위)");
        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content())
                .doesNotContain("](#_Toc", "TOC-Heading"));
    }

    @Test
    void skipsSingleLineTocWithManyInlineLinks() {
        ChunkingProperties properties = properties();
        List<BlockifyGenerationRequest> requests = new ArrayList<>();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> {
                    requests.add(request);
                    return new HeuristicBlockifyGenerator().generate(request);
                });
        String markdown = """
                # 목 차 {#목-차 .TOC-Heading}
                [제 1 장 총칙 [- 6 -](#제-1-장-총칙)](#제-1-장-총칙) [제1조(목적) [- 6 -](#_Toc220912924)](#_Toc220912924) [제2조(적용범위) [- 6 -](#제2조적용범위)](#제2조적용범위) [제3조(사원의 정의) [- 6 -](#_Toc220912926)](#_Toc220912926) [제 2 장 채용 및 근로계약 [- 6 -](#제-2-장-채용-및-근로계약)](#제-2-장-채용-및-근로계약)

                ## 제1조(목적)
                이 규칙은 회사 사원의 채용, 복무 및 근로조건 등에 관한 사항을 정함을 목적으로 한다.
                """;
        ChunkingContext context = ChunkingContext.builder(markdown)
                .sourceDocumentId("mrev-single-line-toc")
                .contentType("markdown")
                .filename("manual.md")
                .objectType("attachment")
                .objectId("18")
                .strategy(ChunkingStrategyType.BLOCKIFY)
                .maxSize(200)
                .overlap(0)
                .build();

        var chunks = chunker.chunk(context);

        assertThat(requests)
                .extracting(BlockifyGenerationRequest::headingPath)
                .containsExactly("제1조(목적)");
        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content())
                .doesNotContain("](#_Toc", "제 2 장 채용"));
    }

    @Test
    void fallsBackWhenAnswerRepeatsHeadingOnly() {
        ChunkingProperties properties = properties();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> List.of(new BlockifyBlock(
                        "제2조(근로시간)",
                        "근로시간 기준은 무엇인가?",
                        "## 제2조(근로시간)",
                        List.of("근로시간"),
                        List.of(),
                        List.of(new BlockifySourceEvidence(
                                "사원의 근로시간은 1일 8시간, 1주 40시간으로 하며 휴게시간은 근로시간 도중에 부여한다.",
                                1, null, null, null, null, List.of("제2조(근로시간)"), "section-1", List.of("p1"))),
                        0.8d)));
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "제2조(근로시간)").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원의 근로시간은 1일 8시간, 1주 40시간으로 하며 휴게시간은 근로시간 도중에 부여한다.")
                        .id("p1")
                        .order(1)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("actualChunkingStrategy", "structure-based")
                .containsEntry("validationStatus", "FALLBACK")
                .containsEntry("fallbackReason", "ANSWER_EQUALS_TITLE");
    }

    @Test
    void fallsBackWhenQuestionIsGeneric() {
        ChunkingProperties properties = properties();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> List.of(new BlockifyBlock(
                        "연차휴가",
                        "연차휴가에 대해 무엇을 확인해야 하는가?",
                        "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 하며, "
                                + "업무 운영에 중대한 지장이 없는 범위에서 사용할 수 있다.",
                        List.of("연차휴가"),
                        List.of(),
                        List.of(new BlockifySourceEvidence(
                                "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 하며, 업무 운영에 중대한 지장이 없는 범위에서 사용할 수 있다.",
                                1, null, null, null, null, List.of("연차휴가"), "section-1", List.of("p1"))),
                        0.8d)));
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "연차휴가").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 하며, 업무 운영에 중대한 지장이 없는 범위에서 사용할 수 있다.")
                        .id("p1")
                        .order(1)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("validationStatus", "FALLBACK")
                .containsEntry("fallbackReason", "GENERIC_QUESTION");
    }

    @Test
    void fallsBackWhenEvidenceIsMissingFromSection() {
        ChunkingProperties properties = properties();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> List.of(new BlockifyBlock(
                        "병가",
                        "병가 신청 기준은 어떻게 정해지는가?",
                        "사원은 질병으로 근무할 수 없는 경우 증빙자료를 제출하여 병가 승인을 받아야 한다.",
                        List.of("병가"),
                        List.of(),
                        List.of(new BlockifySourceEvidence(
                                "원문에 존재하지 않는 병가 증빙 기준 문장입니다.",
                                1, null, null, null, null, List.of("병가"), "section-1", List.of("p1"))),
                        0.8d)));
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "병가").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "회사는 사원이 질병으로 정상 근무가 어려운 경우 취업규칙에 따라 필요한 절차를 안내한다.")
                        .id("p1")
                        .order(1)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("validationStatus", "FALLBACK")
                .containsEntry("fallbackReason", "EVIDENCE_NOT_FOUND");
    }

    @Test
    void parseFailureRecoversWithHeuristicBlockifyChunk() {
        ChunkingProperties properties = properties();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> {
                    throw new IllegalStateException("Failed to parse Blockify LLM response");
                });
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "복무 기준").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원은 근무시간과 휴게시간을 준수해야 하며 회사가 정한 복무 기준에 따라 성실하게 근무해야 한다. "
                                        + "관리자는 근태 기록을 확인하고 위반 사항이 반복되는 경우 취업규칙에 따라 필요한 조치를 안내한다.")
                        .order(1)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content())
                .contains("핵심 질문:", "답변:", "핵심 원문 Evidence:");
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("actualChunkingStrategy", "blockify")
                .containsEntry("validationStatus", "RULE_VALIDATED")
                .containsEntry("generationMode", "RECOVERY_HEURISTIC")
                .containsEntry("primaryFailureReason", "LLM_RESPONSE_PARSE_FAILED")
                .containsEntry("generatorType", "heuristic")
                .containsEntry("generatorModel", "heuristic-blockify-v1")
                .containsKey("blockifyFingerprint")
                .containsKey("sourceEvidence");
    }

    @Test
    void fallsBackWhenAnswerIsTooShort() {
        ChunkingProperties properties = properties();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> List.of(new BlockifyBlock(
                        "교육훈련",
                        "교육훈련 이수 기준은 어떻게 정해지는가?",
                        "교육을 이수해야 한다.",
                        List.of("교육훈련"),
                        List.of(),
                        List.of(new BlockifySourceEvidence(
                                "사원은 회사가 지정한 필수 교육훈련을 정해진 기간 안에 이수해야 하며, 미이수 시 보완 교육 대상이 된다.",
                                1, null, null, null, null, List.of("교육훈련"), "section-1", List.of("p1"))),
                        0.8d)));
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "교육훈련").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "사원은 회사가 지정한 필수 교육훈련을 정해진 기간 안에 이수해야 하며, 미이수 시 보완 교육 대상이 된다.")
                        .id("p1")
                        .order(1)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().toMap())
                .containsEntry("validationStatus", "FALLBACK")
                .containsEntry("fallbackReason", "ANSWER_TOO_SHORT");
    }

    @Test
    void splitsOversizedSingleSourceBlockBeforeGeneration() {
        ChunkingProperties properties = properties();
        properties.getBlockify().setMaxInputTokens(80);
        properties.getBlockify().setMinAnswerChars(20);
        properties.getBlockify().setMinChunkChars(40);
        properties.getBlockify().setMaxChunkChars(2000);
        List<String> requestedSections = new ArrayList<>();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> {
                    requestedSections.add(request.sectionId());
                    NormalizedBlock sourceBlock = request.blocks().get(request.blocks().size() - 1);
                    String answer = sourceBlock.text().length() > 180
                            ? sourceBlock.text().substring(0, 180)
                            : sourceBlock.text();
                    return List.of(new BlockifyBlock(
                            "소설 장면",
                            "이 장면에서 확인할 핵심 내용은 무엇인가?",
                            answer,
                            List.of("소설", "장면"),
                            List.of("소설"),
                            List.of(new BlockifySourceEvidence(
                                    answer,
                                    sourceBlock.order(),
                                    null, null, null, null, List.of("소설"),
                                    request.sectionId(), List.of(sourceBlock.id()))),
                            0.8d));
                });
        String longParagraph = """
                Holden describes leaving Pencey and introduces the school environment with several details about the football game and his own situation. \
                He explains why he was not watching the game and connects that moment to his separation from the school. \
                The narration continues with more context about classmates, teachers, and his attitude toward the people around him. \
                The scene is long enough to exceed the artificial token budget used by this unit test. \
                The chunker should split this single paragraph into smaller source block candidates before calling the generator.
                """;
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "CHAPTER ONE").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, longParagraph)
                        .id("p1")
                        .order(1)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(requestedSections).hasSizeGreaterThan(1);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.metadata().toMap())
                .containsEntry("actualChunkingStrategy", "blockify")
                .containsEntry("chunkType", "ideaBlock")
                .containsKey("sourceEvidence"));
    }

    @Test
    void splitsLongSingleSourceBlockEvenWhenTokenBudgetAllowsIt() {
        ChunkingProperties properties = properties();
        properties.getBlockify().setMaxInputTokens(2500);
        properties.getBlockify().setMinAnswerChars(20);
        properties.getBlockify().setMinChunkChars(40);
        properties.getBlockify().setMaxChunkChars(1200);
        List<String> requestedSections = new ArrayList<>();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> {
                    requestedSections.add(request.sectionId());
                    NormalizedBlock sourceBlock = request.blocks().get(request.blocks().size() - 1);
                    String answer = sourceBlock.text().length() > 160
                            ? sourceBlock.text().substring(0, 160)
                            : sourceBlock.text();
                    return List.of(new BlockifyBlock(
                            "소설 장면",
                            "이 장면에서 확인할 핵심 내용은 무엇인가?",
                            answer,
                            List.of("소설", "장면"),
                            List.of("소설"),
                            List.of(new BlockifySourceEvidence(
                                    answer,
                                    sourceBlock.order(),
                                    null, null, null, null, List.of("소설"),
                                    request.sectionId(), List.of(sourceBlock.id()))),
                            0.8d));
                });
        StringBuilder longParagraph = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            longParagraph.append("Holden describes scene ")
                    .append(i)
                    .append(" with a different school detail, classmate reaction, and personal observation about leaving Pencey. ");
        }
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "CHAPTER ONE").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, longParagraph.toString())
                        .id("p1")
                        .order(1)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(requestedSections).hasSizeGreaterThan(1);
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.metadata().toMap())
                .containsEntry("actualChunkingStrategy", "blockify")
                .containsEntry("chunkType", "ideaBlock"));
    }

    @Test
    void contextMarkdownIngestSplitsLongParagraphBeforeSectionProcessing() {
        ChunkingProperties properties = properties();
        properties.getBlockify().setMaxInputTokens(2500);
        properties.getBlockify().setMinAnswerChars(20);
        properties.getBlockify().setMinChunkChars(40);
        properties.getBlockify().setMaxChunkChars(900);
        List<BlockifyGenerationRequest> requests = new ArrayList<>();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> {
                    requests.add(request);
                    NormalizedBlock sourceBlock = request.blocks().get(request.blocks().size() - 1);
                    String answer = sourceBlock.text().length() > 180
                            ? sourceBlock.text().substring(0, 180)
                            : sourceBlock.text();
                    return List.of(new BlockifyBlock(
                            "소설 장면",
                            "이 장면에서 확인할 핵심 내용은 무엇인가?",
                            answer,
                            List.of("소설", "장면"),
                            List.of("소설"),
                            List.of(new BlockifySourceEvidence(
                                    answer,
                                    sourceBlock.order(),
                                    null, null, null, null, List.of("소설"),
                                    request.sectionId(), List.of(sourceBlock.id()))),
                            0.8d));
                });
        StringBuilder markdown = new StringBuilder("# CHAPTER ONE\n\n");
        for (int i = 0; i < 70; i++) {
            markdown.append("Holden describes scene ")
                    .append(i)
                    .append(" with a different school detail, classmate reaction, and personal observation about leaving Pencey. ");
        }
        ChunkingContext context = ChunkingContext.builder(markdown.toString())
                .sourceDocumentId("mrev-novel")
                .contentType("markdown")
                .filename("novel.md")
                .objectType("attachment")
                .objectId("3")
                .strategy(ChunkingStrategyType.BLOCKIFY)
                .maxSize(200)
                .overlap(0)
                .build();

        var chunks = chunker.chunk(context);

        assertThat(requests).hasSizeGreaterThan(1);
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.metadata().toMap())
                .containsEntry("actualChunkingStrategy", "blockify")
                .containsEntry("chunkType", "ideaBlock")
                .containsKey("sourceBlockRange"));
        assertThat(chunks)
                .extracting(chunk -> chunk.metadata().toMap().get("sourceBlockRange").toString())
                .doesNotHaveDuplicates();
    }

    @Test
    void parseFailureIsIsolatedPerSplitSourceBlock() {
        ChunkingProperties properties = properties();
        properties.getBlockify().setMaxInputTokens(2500);
        properties.getBlockify().setMinAnswerChars(20);
        properties.getBlockify().setMinChunkChars(40);
        properties.getBlockify().setMaxChunkChars(900);
        List<String> requestedSections = new ArrayList<>();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> {
                    requestedSections.add(request.sectionId());
                    throw new IllegalStateException("Failed to parse Blockify LLM response");
                });
        StringBuilder markdown = new StringBuilder("# CHAPTER ONE\n\n");
        for (int i = 0; i < 70; i++) {
            markdown.append("Holden describes scene ")
                    .append(i)
                    .append(" with a different school detail, classmate reaction, and personal observation about leaving Pencey. ");
        }
        ChunkingContext context = ChunkingContext.builder(markdown.toString())
                .sourceDocumentId("mrev-novel")
                .contentType("markdown")
                .filename("novel.md")
                .objectType("attachment")
                .objectId("3")
                .strategy(ChunkingStrategyType.BLOCKIFY)
                .maxSize(200)
                .overlap(0)
                .build();

        var chunks = chunker.chunk(context);

        assertThat(requestedSections).hasSizeGreaterThan(1);
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.metadata().toMap())
                .containsEntry("generationMode", "RECOVERY_HEURISTIC")
                .containsEntry("primaryFailureReason", "LLM_RESPONSE_PARSE_FAILED"));
        assertThat(chunks)
                .extracting(chunk -> chunk.metadata().toMap().get("sourceBlockRange").toString())
                .doesNotHaveDuplicates();
    }

    @Test
    void largeLlmCandidateSetCallsGeneratorPerSmallCandidateInsteadOfGlobalLocalFallback() {
        ChunkingProperties properties = properties();
        properties.getBlockify().setGeneratorType("llm");
        properties.getBlockify().setMaxBlocksPerSection(3);
        properties.getBlockify().setMaxInputTokens(2500);
        properties.getBlockify().setMinAnswerChars(20);
        properties.getBlockify().setMinChunkChars(40);
        properties.getBlockify().setMaxChunkChars(700);
        List<BlockifyGenerationRequest> externalRequests = new ArrayList<>();
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> {
                    externalRequests.add(request);
                    NormalizedBlock sourceBlock = request.blocks().get(request.blocks().size() - 1);
                    String evidence = sourceBlock.text().split("\\.")[0].trim() + ".";
                    String answer = evidence + " 이 장면은 Holden이 Pencey 학교와 떠나는 상황을 설명한다.";
                    return List.of(new BlockifyBlock(
                            "장면 요약",
                            "장면 요약",
                            "Holden은 Pencey 학교 장면에서 무엇을 설명하는가?",
                            answer,
                            List.of("Holden", "Pencey"),
                            List.of("narrative"),
                            "Holden",
                            "character",
                            List.of(new BlockifySourceEvidence(
                                    evidence,
                                    sourceBlock.order(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    List.of(),
                                    request.sectionId(),
                                    List.of())),
                            null,
                            request.sectionId(),
                            0.8d));
                });
        StringBuilder markdown = new StringBuilder("# CHAPTER ONE\n\n");
        for (int i = 0; i < 40; i++) {
            markdown.append("Holden describes scene ")
                    .append(i)
                    .append(" with Pencey school details and a personal observation about leaving the school. ");
        }
        ChunkingContext context = ChunkingContext.builder(markdown.toString())
                .sourceDocumentId("mrev-novel")
                .contentType("markdown")
                .filename("novel.md")
                .objectType("attachment")
                .objectId("3")
                .strategy(ChunkingStrategyType.BLOCKIFY)
                .maxSize(200)
                .overlap(0)
                .build();

        var chunks = chunker.chunk(context);

        assertThat(externalRequests).hasSizeGreaterThan(properties.getBlockify().getMaxBlocksPerSection());
        assertThat(externalRequests).allSatisfy(request -> assertThat(request.blocks().stream()
                .filter(block -> block.type() != NormalizedBlockType.HEADING)
                .toList()).hasSizeLessThanOrEqualTo(1));
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.metadata().toMap())
                .containsEntry("actualChunkingStrategy", "blockify")
                .doesNotContainEntry("generationMode", "LOCAL_HEURISTIC")
                .doesNotContainEntry("externalGeneratorSkipped", true));
        assertThat(chunks).allSatisfy(chunk -> {
            Map<String, Object> metadata = chunk.metadata().toMap();
            assertThat(metadata).containsEntry("requestedChunkingStrategy", "blockify");
            if ("structure-based".equals(metadata.get("actualChunkingStrategy"))) {
                assertThat(metadata)
                        .containsEntry("validationStatus", "FALLBACK")
                        .containsKey("fallbackReason");
            }
        });
    }

    @Test
    void llmGenerationTimeoutFallsBackWithReasonMetadata() {
        ChunkingProperties properties = properties();
        properties.getBlockify().setGeneratorType("llm");
        properties.getBlockify().setGenerationTimeout(Duration.ofMillis(50));
        BlockifyChunker chunker = new BlockifyChunker(
                properties.getBlockify(),
                new StructureBasedChunker(200, 0, new RecursiveChunker(200, 0)),
                request -> {
                    try {
                        Thread.sleep(5_000L);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    return List.of();
                });
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "CHAPTER ONE")
                        .id("h1")
                        .order(0)
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "Holden describes Pencey Prep and explains that he was leaving the school after failing multiple subjects.")
                        .id("p1")
                        .order(1)
                        .build()));

        var chunks = chunker.chunk(document, context(document));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.metadata().toMap())
                .containsEntry("requestedChunkingStrategy", "blockify")
                .containsEntry("generationMode", "RECOVERY_HEURISTIC")
                .containsEntry("primaryFailureReason", "LLM_GENERATION_TIMEOUT"));
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

    private NormalizedDocument documentWithBlockifyLlm(List<NormalizedBlock> blocks, String provider, String model) {
        return NormalizedDocument.builder("mrev-1")
                .sourceFormat("markdown")
                .filename("manual.md")
                .blocks(blocks)
                .metadata(java.util.Map.of(
                        ChunkMetadata.KEY_OBJECT_TYPE, "attachment",
                        ChunkMetadata.KEY_OBJECT_ID, "17",
                        "blockifyLlmProvider", provider,
                        "blockifyLlmModel", model))
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
