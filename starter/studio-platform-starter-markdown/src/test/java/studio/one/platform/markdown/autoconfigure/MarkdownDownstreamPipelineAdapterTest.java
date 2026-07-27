package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.service.pipeline.InMemoryRagChunkStageStore;
import studio.one.platform.ai.service.pipeline.RagChunkStage;
import studio.one.platform.ai.service.pipeline.RagChunkStageStore;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.artifact.ChunkSet;
import studio.one.platform.chunking.artifact.ChunkSetStore;
import studio.one.platform.chunking.artifact.InMemoryChunkSetStore;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.documentmetadata.DocumentMetadataClassification;
import studio.one.platform.documentmetadata.DocumentMetadataField;
import studio.one.platform.documentmetadata.DocumentMetadataProvenance;
import studio.one.platform.documentmetadata.DocumentMetadataQuality;
import studio.one.platform.documentmetadata.DocumentSemanticType;
import studio.one.platform.documentmetadata.DocumentSemanticTypeSelection;
import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.application.MarkdownPipelineProgress;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeApplyOptions;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeApplyResult;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergePreview;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergePreviewOptions;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeUndoOptions;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeUndoResult;
import studio.one.platform.markdown.application.MarkdownIdeaBlockSummary;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.markdown.domain.MarkdownResource;
import studio.one.platform.markdown.domain.MarkdownRevision;
import studio.one.platform.markdown.domain.MarkdownRevisionStatus;
import studio.one.platform.skillgraph.application.usecase.SkillRagExtractionJobService;

class MarkdownDownstreamPipelineAdapterTest {

    @Test
    void automaticallySelectsRecursiveForPlainMarkdownFallback() {
        ChunkingOrchestrator chunking = mock(ChunkingOrchestrator.class);
        MarkdownRepository repository = mock(MarkdownRepository.class);
        when(repository.findResources("revision-1")).thenReturn(List.of());
        when(repository.findLocators("revision-1")).thenReturn(List.of());
        when(chunking.chunk(any(NormalizedDocument.class), any(ChunkingContext.class)))
                .thenReturn(List.of(new Chunk("chunk-1", "content",
                        ChunkMetadata.builder(ChunkingStrategyType.RECURSIVE, 0).build())));
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class, chunking),
                provider(RagChunkStageStore.class),
                provider(EmbeddingPort.class),
                provider(AiProviderRegistry.class),
                provider(ChunkSetStore.class),
                repository,
                new ObjectMapper());

        int count = adapter.estimateChunkCount(revision(), new MarkdownPipelineOptions(true, false, false));

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<ChunkingContext> context = ArgumentCaptor.forClass(ChunkingContext.class);
        verify(chunking).chunk(any(NormalizedDocument.class), context.capture());
        assertThat(context.getValue().strategy()).isEqualTo(ChunkingStrategyType.RECURSIVE);
        assertThat(context.getValue().metadata())
                .containsEntry("objectType", "attachment")
                .containsEntry("chunkingStrategySelectionMode", "AUTO")
                .containsEntry("chunkingStrategySelectionReason", "PLAIN_TEXT_ONLY")
                .containsEntry("selectedChunkingStrategy", "recursive");
    }

    @Test
    void forwardsChunkingAndEmbeddingSelections() {
        RagIndexJobService ragJobs = completedJobService();
        ChunkingOrchestrator chunking = mock(ChunkingOrchestrator.class);
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        ChunkSetStore chunkSetStore = new InMemoryChunkSetStore();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        when(repository.findLocators("revision-1")).thenReturn(List.of());
        when(chunking.chunk(any(NormalizedDocument.class), any(ChunkingContext.class)))
                .thenReturn(List.of(new Chunk("chunk-1", "content",
                        ChunkMetadata.builder(ChunkingStrategyType.FIXED_SIZE, 0).build())));
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class, ragJobs),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class, chunking),
                provider(RagChunkStageStore.class, stageStore),
                provider(EmbeddingPort.class),
                provider(AiProviderRegistry.class),
                provider(ChunkSetStore.class, chunkSetStore),
                repository,
                new ObjectMapper());
        MarkdownPipelineOptions options = new MarkdownPipelineOptions(
                true, true, false,
                "fixed-size", 400, 40, "token",
                null, "google", "gemini-embedding-001", 768,
                true, false, null, null, null);

        adapter.process(revision(), options);

        ArgumentCaptor<ChunkingContext> context = ArgumentCaptor.forClass(ChunkingContext.class);
        verify(chunking).chunk(any(NormalizedDocument.class), context.capture());
        assertThat(context.getValue().strategy()).isEqualTo(ChunkingStrategyType.FIXED_SIZE);
        assertThat(context.getValue().maxSize()).isEqualTo(400);
        assertThat(context.getValue().overlap()).isEqualTo(40);

        ArgumentCaptor<RagIndexJobCreateRequest> request =
                ArgumentCaptor.forClass(RagIndexJobCreateRequest.class);
        ArgumentCaptor<RagIndexJobSourceRequest> sourceRequest =
                ArgumentCaptor.forClass(RagIndexJobSourceRequest.class);
        verify(ragJobs).createJob(request.capture(), sourceRequest.capture());
        assertThat(request.getValue().sourceType()).isEqualTo("attachment");
        assertThat(request.getValue().objectType()).isEqualTo("attachment");
        assertThat(request.getValue().objectId()).isEqualTo("42");
        assertThat(request.getValue().indexRequest()).isNull();
        assertThat(sourceRequest.getValue().embeddingProvider()).isEqualTo("google");
        assertThat(sourceRequest.getValue().embeddingModel()).isEqualTo("gemini-embedding-001");
        assertThat(sourceRequest.getValue().useLlmKeywordExtraction()).isTrue();
        assertThat(sourceRequest.getValue().requirePreparedChunks()).isTrue();
        assertThat(sourceRequest.getValue().chunkSetId()).startsWith("cset-");
        assertThat(sourceRequest.getValue().metadata())
                .containsEntry("markdownRevisionId", "revision-1")
                .containsEntry("strategy", "fixed-size")
                .containsEntry("embeddingDimension", 768);
        ChunkSet stored = chunkSetStore.findById(sourceRequest.getValue().chunkSetId()).orElseThrow();
        assertThat(stored.items()).singleElement().satisfies(item -> {
            assertThat(item.text()).isEqualTo("content");
            assertThat(item.metadata()).containsEntry("strategy", "fixed-size")
                    .containsEntry("ragRechunkApplied", false);
        });
        verify(ragJobs).startJob("rag-job-1");
    }

    @Test
    void projectsMetadataCreatedDuringEnrichmentIntoChunks() throws Exception {
        ChunkingOrchestrator chunking = mock(ChunkingOrchestrator.class);
        MarkdownRepository repository = mock(MarkdownRepository.class);
        AtomicReference<MarkdownResource> metadataResource = new AtomicReference<>();
        when(repository.findResources("revision-1")).thenReturn(List.of());
        when(repository.findLocators("revision-1")).thenReturn(List.of());
        when(repository.findResource("revision-1", "DOCUMENT_METADATA"))
                .thenAnswer(invocation -> Optional.ofNullable(metadataResource.get()));
        when(chunking.chunk(any(NormalizedDocument.class), any(ChunkingContext.class)))
                .thenReturn(List.of(new Chunk("chunk-1", "content",
                        ChunkMetadata.builder(ChunkingStrategyType.RECURSIVE, 0).build())));

        DocumentMetadataArtifact artifact = new DocumentMetadataArtifact(
                "metadata-1",
                "revision-1",
                "schema-1",
                "extractor-1",
                "fingerprint-1",
                new DocumentMetadataClassification(
                        "PROFESSIONAL_BOOK",
                        "PROFESSIONAL_BOOK",
                        DocumentSemanticTypeSelection.BOOK,
                        DocumentSemanticType.BOOK,
                        DocumentSemanticType.BOOK,
                        "HUMANITIES",
                        0.98d,
                        "classifier-1",
                        null,
                        null,
                        null,
                        null),
                DocumentMetadataQuality.COMPLETE,
                Map.of("title", new DocumentMetadataField(
                        "title",
                        List.of("미국은 왜 전쟁을 멈추지 못하는가"),
                        List.of("미국은 왜 전쟁을 멈추지 못하는가"),
                        0.98d,
                        DocumentMetadataProvenance.SOURCE_VERIFIED,
                        List.of())),
                List.of());
        ObjectMapper objectMapper = new ObjectMapper();
        String artifactJson = objectMapper.writeValueAsString(artifact);
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class, chunking),
                provider(RagChunkStageStore.class, stageStore),
                provider(EmbeddingPort.class),
                provider(AiProviderRegistry.class),
                provider(ChunkSetStore.class),
                (revision, options) -> metadataResource.set(new MarkdownResource(
                        "metadata-1",
                        revision.revisionId(),
                        "DOCUMENT_METADATA",
                        "document-metadata.json",
                        null,
                        artifactJson)),
                repository,
                objectMapper);

        adapter.process(revision(), new MarkdownPipelineOptions(true, false, false));

        ArgumentCaptor<ChunkingContext> context = ArgumentCaptor.forClass(ChunkingContext.class);
        verify(chunking).chunk(any(NormalizedDocument.class), context.capture());
        assertThat(context.getValue().metadata())
                .containsEntry("docMetadataId", "metadata-1")
                .containsEntry("docSemanticType", "BOOK")
                .containsEntry("docTitle", "미국은 왜 전쟁을 멈추지 못하는가");
    }

    @Test
    void forwardsSkillCandidateEmbeddingSelection() {
        SkillRagExtractionJobService skillJobs = mock(SkillRagExtractionJobService.class);
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class, skillJobs),
                provider(ChunkingOrchestrator.class),
                provider(RagChunkStageStore.class),
                mock(MarkdownRepository.class));
        MarkdownPipelineOptions options = new MarkdownPipelineOptions(
                true, true, true,
                null, null, null, null,
                "retrieval-ko-kure", null, null, null,
                false, "llm", true, "kure", "nlpai-lab/KURE-v1", 1024);

        adapter.process(
                revision(),
                options,
                studio.one.platform.markdown.domain.MarkdownPipelineStage.SKILL_EXTRACTION,
                stage -> {
                });

        verify(skillJobs).submitAllChunks(
                "attachment",
                "42",
                null,
                false,
                true,
                "kure",
                "nlpai-lab/KURE-v1",
                1024,
                "llm");
    }

    @Test
    void resumesFromRagWithoutRepeatingChunking() {
        RagIndexJobService ragJobs = completedJobService();
        ChunkingOrchestrator chunking = mock(ChunkingOrchestrator.class);
        RagChunkStageStore stageStore = preparedStageStore();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class, ragJobs),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class, chunking),
                provider(RagChunkStageStore.class, stageStore),
                repository);

        adapter.process(revision(), new MarkdownPipelineOptions(true, true, false),
                studio.one.platform.markdown.domain.MarkdownPipelineStage.RAG_INDEX, stage -> {
                });

        verify(chunking, never()).chunk(any(NormalizedDocument.class), any(ChunkingContext.class));
        verify(ragJobs).startJob("rag-job-1");
    }

    @Test
    void blockifyUsesSectionLocatorsEvenWhenPageLocatorsExist() {
        ChunkingOrchestrator chunking = mock(ChunkingOrchestrator.class);
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        String markdown = "# 제1장 총칙\n\n## 제1조(목적)\n이 규칙은 회사의 복무 기준을 정하는 것을 목적으로 한다. "
                + "사원은 근무시간, 휴게, 휴일, 휴가, 징계 및 퇴직 등 복무 전반에 관한 기준을 준수해야 한다.\n\n"
                + "## 제2조(근로시간)\n사원의 근로시간은 1일 8시간, 1주 40시간으로 하며 휴게시간은 근로시간 도중에 부여한다.";
        MarkdownRevision revision = revision(markdown);
        int firstStart = markdown.indexOf("## 제1조");
        int secondStart = markdown.indexOf("## 제2조");
        when(repository.findLocators("revision-1")).thenReturn(List.of(
                new MarkdownLocator("page-1", "revision-1", "PAGE", 1, "1쪽", 0, markdown.length(),
                        "page-1", "{}"),
                new MarkdownLocator("section-1", "revision-1", "SECTION", 1, "제1조(목적)",
                        firstStart, secondStart, "section-1", "{}"),
                new MarkdownLocator("section-2", "revision-1", "SECTION", 2, "제2조(근로시간)",
                        secondStart, markdown.length(), "section-2", "{}")));
        when(chunking.chunk(any(NormalizedDocument.class), any(ChunkingContext.class)))
                .thenReturn(List.of(new Chunk("chunk-1",
                        "제목: 제1조(목적)\n\n핵심 질문:\n복무 기준은 무엇인가?\n\n답변:\n복무 기준을 정하는 것을 목적으로 한다.\n\n핵심 원문 Evidence:\n- 복무 기준을 정하는 것을 목적으로 한다.",
                        ChunkMetadata.builder(ChunkingStrategyType.BLOCKIFY, 0)
                                .attributes(blockifyMetadata())
                                .build())));
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class, chunking),
                provider(RagChunkStageStore.class, stageStore),
                repository);

        adapter.process(revision, new MarkdownPipelineOptions(
                true, false, false,
                "blockify", 800, 100, "token",
                null, null, null, null,
                false, null, false, null, null, null));

        ArgumentCaptor<NormalizedDocument> document = ArgumentCaptor.forClass(NormalizedDocument.class);
        verify(chunking).chunk(document.capture(), any(ChunkingContext.class));
        assertThat(document.getValue().blocks()).hasSize(2);
        assertThat(document.getValue().blocks()).extracting(block -> block.metadata().get("locatorType"))
                .containsExactly("SECTION", "SECTION");
        assertThat(document.getValue().blocks()).extracting(block -> block.type())
                .containsExactly(
                        studio.one.platform.chunking.core.NormalizedBlockType.DOCUMENT,
                        studio.one.platform.chunking.core.NormalizedBlockType.DOCUMENT);
        assertThat(document.getValue().blocks()).extracting(block -> block.text())
                .allSatisfy(text -> assertThat(text).doesNotContain("# 제1장 총칙"));
        List<RagChunkStage> stages = stageStore.findByObject("attachment", "42", "document-1");
        assertThat(stages).hasSize(1);
        assertThat(stages.get(0).metadata())
                .containsEntry("requestedChunkingStrategy", "blockify")
                .containsEntry("actualChunkingStrategy", "blockify")
                .containsEntry("schemaVersion", "blockify-metadata-v1")
                .containsKey("blockifyFingerprint")
                .containsKey("sourceEvidence");
    }

    @Test
    void usesNormalizedDocumentSnapshotBeforeLocatorFallback() {
        ChunkingOrchestrator chunking = mock(ChunkingOrchestrator.class);
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        NormalizedDocument snapshotDocument = NormalizedDocument.builder("document-1")
                .plainText("normalized text")
                .sourceFormat("markdown")
                .filename("sample.md")
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.HEADING, "Normalized Heading")
                        .id("block-1")
                        .sourceRef("source-1")
                        .order(0)
                        .build()))
                .metadata(Map.of("normalizationSource", "PANDOC_MARKDOWN"))
                .build();
        MarkdownResource snapshot = NormalizedDocumentSnapshot.resource("revision-1", snapshotDocument,
                NormalizedDocumentSnapshot.SOURCE_PANDOC, List.of(), new ObjectMapper());
        when(repository.findResources("revision-1")).thenReturn(List.of(snapshot));
        when(repository.findLocators("revision-1")).thenReturn(List.of(new MarkdownLocator(
                "locator-1", "revision-1", "SECTION", 1, "Fallback", 0, 10, "fallback", "{}")));
        when(chunking.chunk(any(NormalizedDocument.class), any(ChunkingContext.class)))
                .thenReturn(List.of(new Chunk("chunk-1", "content",
                        ChunkMetadata.builder(ChunkingStrategyType.RECURSIVE, 0)
                                .attributes(Map.of(
                                        "page", 1,
                                        "pdfExtractionParts", List.of(Map.of("pageFrom", 1, "pageTo", 100)),
                                        "pageQuality", List.of(Map.of("page", 1, "score", 0.8d)),
                                        ChunkMetadata.KEY_PARENT_CHUNK_CONTENT, "document-wide parent content",
                                        ChunkMetadata.KEY_PARENT_CHUNK_BLOCK_IDS, List.of("parent-block-1"),
                                        ChunkMetadata.KEY_PARENT_CHUNK_SOURCE_REFS, List.of("page[1]/parent"),
                                        ChunkMetadata.KEY_SOURCE_REFS, List.of("page[1]/child")))
                                .parentChunkId("parent-1")
                                .blockIds(List.of("child-block-1"))
                                .build())));
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class, chunking),
                provider(RagChunkStageStore.class, stageStore),
                provider(EmbeddingPort.class),
                provider(AiProviderRegistry.class),
                repository,
                new ObjectMapper());

        adapter.process(revision(), new MarkdownPipelineOptions(true, false, false));

        ArgumentCaptor<NormalizedDocument> document = ArgumentCaptor.forClass(NormalizedDocument.class);
        verify(chunking).chunk(document.capture(), any(ChunkingContext.class));
        assertThat(document.getValue().blocks()).hasSize(1);
        assertThat(document.getValue().blocks().get(0).text()).isEqualTo("Normalized Heading");
        assertThat(document.getValue().metadata())
                .containsEntry("normalizedSnapshotUsed", true)
                .containsEntry("normalizationSource", "PANDOC_MARKDOWN")
                .containsEntry("normalizationStatus", "VALID")
                .containsEntry("contentBlockCount", 1)
                .containsEntry("mathBlockCount", 0)
                .containsEntry("searchablePageCoverage", 0.0d);
        assertThat(stageStore.findByObject("attachment", "42", "document-1").get(0).metadata())
                .containsEntry("page", 1)
                .containsEntry(ChunkMetadata.KEY_PARENT_CHUNK_ID, "parent-1")
                .containsEntry(ChunkMetadata.KEY_BLOCK_IDS, List.of("child-block-1"))
                .containsEntry(ChunkMetadata.KEY_SOURCE_REFS, List.of("page[1]/child"))
                .doesNotContainKeys(
                        "pdfExtractionParts",
                        "pageQuality",
                        ChunkMetadata.KEY_PARENT_CHUNK_CONTENT,
                        ChunkMetadata.KEY_PARENT_CHUNK_BLOCK_IDS,
                        ChunkMetadata.KEY_PARENT_CHUNK_SOURCE_REFS);
    }

    @Test
    void blocksRagIndexWhenNormalizedQualityGateRejectsDocument() {
        RagIndexJobService ragJobs = mock(RagIndexJobService.class);
        ChunkingOrchestrator chunking = mock(ChunkingOrchestrator.class);
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        NormalizedDocument snapshotDocument = NormalizedDocument.builder("document-1")
                .plainText("손상된 ㅠ산 본문")
                .metadata(Map.of(
                        "ragIndexEligible", false,
                        "qualityGateStatus", "BLOCKED",
                        "markdownQualityIssues", List.of("KOREAN_JAMO_REVIEW_REQUIRED")))
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "손상된 ㅠ산 본문")
                        .page(1).sourceRef("page[1]/block[0]").order(0).build()))
                .build();
        MarkdownResource snapshot = NormalizedDocumentSnapshot.resource("revision-1", snapshotDocument,
                NormalizedDocumentSnapshot.SOURCE_NATIVE, List.of("KOREAN_JAMO_REVIEW_REQUIRED"),
                new ObjectMapper());
        when(repository.findResources("revision-1")).thenReturn(List.of(snapshot));
        when(repository.findLocators("revision-1")).thenReturn(List.of());
        when(chunking.chunk(any(NormalizedDocument.class), any(ChunkingContext.class)))
                .thenReturn(List.of(new Chunk("chunk-1", "content",
                        ChunkMetadata.builder(ChunkingStrategyType.RECURSIVE, 0).build())));
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class, ragJobs),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class, chunking),
                provider(RagChunkStageStore.class, stageStore),
                repository);

        assertThatThrownBy(() -> adapter.process(revision(), new MarkdownPipelineOptions(true, true, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("quality gate blocked RAG indexing")
                .hasMessageContaining("KOREAN_JAMO_REVIEW_REQUIRED");
        verify(ragJobs, never()).createJob(any(), any());
    }

    @Test
    void blockifyRagRequiresStagedChunks() {
        RagIndexJobService ragJobs = completedJobService();
        ChunkingOrchestrator chunking = mock(ChunkingOrchestrator.class);
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        when(repository.findLocators("revision-1")).thenReturn(List.of());
        when(chunking.chunk(any(NormalizedDocument.class), any(ChunkingContext.class)))
                .thenReturn(List.of(new Chunk("chunk-1",
                        "제목: 제1조(목적)\n\n핵심 질문:\n복무 기준은 무엇인가?\n\n답변:\n복무 기준을 정하는 것을 목적으로 한다.\n\n핵심 원문 Evidence:\n- 복무 기준을 정하는 것을 목적으로 한다.",
                        ChunkMetadata.builder(ChunkingStrategyType.BLOCKIFY, 0)
                                .attributes(blockifyMetadata())
                                .build())));
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class, ragJobs),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class, chunking),
                provider(RagChunkStageStore.class, stageStore),
                repository);

        adapter.process(revision(), new MarkdownPipelineOptions(
                true, true, false,
                "blockify", 800, 100, "token",
                null, null, null, null,
                false, null, false, null, null, null));

        ArgumentCaptor<RagIndexJobSourceRequest> sourceRequest =
                ArgumentCaptor.forClass(RagIndexJobSourceRequest.class);
        verify(ragJobs).createJob(any(RagIndexJobCreateRequest.class), sourceRequest.capture());
        assertThat(sourceRequest.getValue().metadata())
                .containsEntry("requireRagChunkStage", true)
                .containsEntry("strategy", "blockify");
    }

    @Test
    void forwardsBlockifyLlmSelectionToChunkingContext() {
        ChunkingOrchestrator chunking = mock(ChunkingOrchestrator.class);
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        when(repository.findLocators("revision-1")).thenReturn(List.of());
        when(chunking.chunk(any(NormalizedDocument.class), any(ChunkingContext.class)))
                .thenReturn(List.of(new Chunk("chunk-1",
                        "제목: 제1조(목적)\n\n핵심 질문:\n복무 기준은 무엇인가?\n\n답변:\n복무 기준을 정하는 것을 목적으로 한다.\n\n핵심 원문 Evidence:\n- 복무 기준을 정하는 것을 목적으로 한다.",
                        ChunkMetadata.builder(ChunkingStrategyType.BLOCKIFY, 0)
                                .attributes(blockifyMetadata())
                                .build())));
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class, chunking),
                provider(RagChunkStageStore.class, stageStore),
                repository);

        adapter.process(revision(), new MarkdownPipelineOptions(
                true, false, false,
                "blockify", 800, 100, "token",
                "google-ai-gemini", "gemini-2.5-flash", null,
                null, null, null, null,
                false, null, false, null, null, null));

        ArgumentCaptor<ChunkingContext> context = ArgumentCaptor.forClass(ChunkingContext.class);
        verify(chunking).chunk(any(NormalizedDocument.class), context.capture());
        assertThat(context.getValue().metadata())
                .containsEntry("blockifyLlmProvider", "google-ai-gemini")
                .containsEntry("blockifyLlmModel", "gemini-2.5-flash");
        assertThat(stageStore.findByObject("attachment", "42", "document-1").get(0).metadata())
                .containsEntry("blockifyLlmProvider", "google-ai-gemini")
                .containsEntry("blockifyLlmModel", "gemini-2.5-flash");
    }

    @Test
    void failsBlockifyPipelineWhenNoChunksAreGenerated() {
        ChunkingOrchestrator chunking = mock(ChunkingOrchestrator.class);
        RagChunkStageStore stageStore = mock(RagChunkStageStore.class);
        MarkdownRepository repository = mock(MarkdownRepository.class);
        when(repository.findLocators("revision-1")).thenReturn(List.of());
        when(chunking.chunk(any(NormalizedDocument.class), any(ChunkingContext.class)))
                .thenReturn(List.of());
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class, chunking),
                provider(RagChunkStageStore.class, stageStore),
                repository);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.process(
                revision(),
                new MarkdownPipelineOptions(
                        true, false, false,
                        "blockify", 800, 100, "token",
                        null, null, null, null,
                        false, null, false, null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Blockify chunking produced no chunks");

        verify(stageStore, never()).replace(any(), any(), any(), any());
    }

    @Test
    void failsBlockifyPipelineWhenStageStoreDoesNotPersistChunks() {
        ChunkingOrchestrator chunking = mock(ChunkingOrchestrator.class);
        RagChunkStageStore stageStore = mock(RagChunkStageStore.class);
        MarkdownRepository repository = mock(MarkdownRepository.class);
        when(repository.findLocators("revision-1")).thenReturn(List.of());
        when(chunking.chunk(any(NormalizedDocument.class), any(ChunkingContext.class)))
                .thenReturn(List.of(new Chunk("chunk-1",
                        "제목: 제1조(목적)\n\n핵심 질문:\n복무 기준은 무엇인가?\n\n답변:\n복무 기준을 정하는 것을 목적으로 한다.\n\n핵심 원문 Evidence:\n- 복무 기준을 정하는 것을 목적으로 한다.",
                        ChunkMetadata.builder(ChunkingStrategyType.BLOCKIFY, 0)
                                .attributes(blockifyMetadata())
                                .build())));
        when(stageStore.findByObject("attachment", "42", "document-1")).thenReturn(List.of());
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class, chunking),
                provider(RagChunkStageStore.class, stageStore),
                repository);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.process(
                revision(),
                new MarkdownPipelineOptions(
                        true, false, false,
                        "blockify", 800, 100, "token",
                        null, null, null, null,
                        false, null, false, null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Blockify chunk stage was not stored")
                .hasMessageContaining("expected=1, actual=0");
    }

    @Test
    void failsBlockifyPipelineWhenChunkMetadataIsIncomplete() {
        ChunkingOrchestrator chunking = mock(ChunkingOrchestrator.class);
        RagChunkStageStore stageStore = mock(RagChunkStageStore.class);
        MarkdownRepository repository = mock(MarkdownRepository.class);
        when(repository.findLocators("revision-1")).thenReturn(List.of());
        when(chunking.chunk(any(NormalizedDocument.class), any(ChunkingContext.class)))
                .thenReturn(List.of(new Chunk("chunk-1", "plain content",
                        ChunkMetadata.builder(ChunkingStrategyType.BLOCKIFY, 0).build())));
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class, chunking),
                provider(RagChunkStageStore.class, stageStore),
                repository);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.process(
                revision(),
                new MarkdownPipelineOptions(
                        true, false, false,
                        "blockify", 800, 100, "token",
                        null, null, null, null,
                        false, null, false, null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid blockify chunk metadata")
                .hasMessageContaining("requestedChunkingStrategy=blockify");

        verify(stageStore, never()).replace(any(), any(), any(), any());
    }

    @Test
    void preservesAndCompletesBlockifyFallbackMetadata() {
        ChunkingOrchestrator chunking = mock(ChunkingOrchestrator.class);
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        when(repository.findLocators("revision-1")).thenReturn(List.of());
        when(chunking.chunk(any(NormalizedDocument.class), any(ChunkingContext.class)))
                .thenReturn(List.of(new Chunk("chunk-1", "fallback content",
                        ChunkMetadata.builder(ChunkingStrategyType.STRUCTURE_BASED, 0)
                                .attributes(Map.of("fallbackReason", "TABLE_SECTION"))
                                .build())));
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class, chunking),
                provider(RagChunkStageStore.class, stageStore),
                repository);

        adapter.process(
                revision(),
                new MarkdownPipelineOptions(
                        true, false, false,
                        "blockify", 800, 100, "token",
                        null, null, null, null,
                        false, null, false, null, null, null));

        List<RagChunkStage> stages = stageStore.findByObject("attachment", "42", "document-1");
        assertThat(stages).hasSize(1);
        assertThat(stages.get(0).metadata())
                .containsEntry("requestedChunkingStrategy", "blockify")
                .containsEntry("actualChunkingStrategy", "structure-based")
                .containsEntry("validationStatus", "FALLBACK")
                .containsEntry("fallbackReason", "TABLE_SECTION");
    }

    @Test
    void reportsLatestChunkingProgressFromStagedBlockifyMetadata() {
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class),
                provider(RagChunkStageStore.class, stageStore),
                mock(MarkdownRepository.class));
        stageStore.replace("attachment", "42", "document-1", List.of(
                new RagChunkStage("attachment", "42", "document-1", 0, "chunk-1", "idea block",
                        Map.ofEntries(
                                Map.entry("chunkType", "ideaBlock"),
                                Map.entry("requestedDocumentType", "auto"),
                                Map.entry("detectedDocumentType", "policy"),
                                Map.entry("documentTypeConfidence", 0.91d),
                                Map.entry("documentTypeReason", "policy signals"),
                                Map.entry("blockifyProfile", "policy-v1"),
                                Map.entry("ideaBlockSchemaVersion", "ideablock-policy-v1"),
                                Map.entry("actualChunkingStrategy", "blockify"),
                                Map.entry("schemaVersion", "blockify-metadata-v1"),
                                Map.entry("requestedChunkingStrategy", "blockify"),
                                Map.entry("validationStatus", "RULE_VALIDATED"),
                                Map.entry("typedFields", Map.of(
                                        "articleNo", "제1조",
                                        "obligation", "복무 기준을 준수해야 한다.")),
                                Map.entry("sourceBlockRange", Map.of("start", 1, "end", 1)),
                                Map.entry("confidence", 0.8d),
                                Map.entry("ideaBlockMergeCandidate", true),
                                Map.entry("ideaBlockSimilarityClusterId", "sim-1"),
                                Map.entry("ideaBlockSimilarityClusterSize", 2),
                                Map.entry("ideaBlockSimilarityMaxScore", 0.91d),
                                Map.entry("ideaBlockMergePolicy", "candidate-only"),
                                Map.entry("ideaBlockSourceBlockTargetCount", 2),
                                Map.entry("ideaBlockSourceBlockCoveredCount", 2)),
                        null),
                new RagChunkStage("attachment", "42", "document-1", 1, "chunk-2", "idea block",
                        Map.ofEntries(
                                Map.entry("chunkType", "ideaBlock"),
                                Map.entry("actualChunkingStrategy", "blockify"),
                                Map.entry("typedFields", Map.of("articleNo", "제2조")),
                                Map.entry("sourceBlockRange", Map.of("start", 2, "end", 2)),
                                Map.entry("confidence", 0.6d),
                                Map.entry("ideaBlockMergeCandidate", true),
                                Map.entry("ideaBlockSimilarityClusterId", "sim-1"),
                                Map.entry("ideaBlockSimilarityClusterSize", 2),
                                Map.entry("ideaBlockSimilarityMaxScore", 0.91d),
                                Map.entry("ideaBlockMergePolicy", "candidate-only"),
                                Map.entry("ideaBlockSourceBlockTargetCount", 3),
                                Map.entry("ideaBlockSourceBlockCoveredCount", 3)),
                        null),
                new RagChunkStage("attachment", "42", "document-1", 2, "chunk-3", "fallback",
                        Map.of(
                                "requestedChunkingStrategy", "blockify",
                                "actualChunkingStrategy", "structure-based",
                                "validationStatus", "FALLBACK",
                                "fallbackReason", "TABLE_SECTION",
                                "sourceBlockRange", Map.of("start", 3, "end", 3)),
                        null)));

        MarkdownPipelineProgress.ChunkingProgress progress = adapter.latestChunkingProgress(revision());

        assertThat(progress).isNotNull();
        assertThat(progress.chunkCount()).isEqualTo(3);
        assertThat(progress.ideaBlockCount()).isEqualTo(2);
        assertThat(progress.fallbackCount()).isEqualTo(1);
        assertThat(progress.qualityStatus()).isEqualTo("IDEABLOCK_PARTIAL_FALLBACK");
        assertThat(progress.fallbackReasonCounts()).containsEntry("TABLE_SECTION", 1);
        assertThat(progress.sourceBlockTargetCount()).isEqualTo(3);
        assertThat(progress.sourceBlockCoveredCount()).isEqualTo(3);
        assertThat(progress.sourceBlockCoverage()).isEqualTo(1.0d);
        assertThat(progress.averageConfidence()).isEqualTo(0.7d);

        MarkdownIdeaBlockSummary summary = adapter.ideaBlockSummary(revision());
        assertThat(summary.ideaBlockCount()).isEqualTo(2);
        assertThat(summary.fallbackCount()).isEqualTo(1);
        assertThat(summary.qualityStatus()).isEqualTo("IDEABLOCK_PARTIAL_FALLBACK");
        assertThat(summary.mergeCandidateCount()).isEqualTo(2);
        assertThat(summary.similarityClusterCount()).isEqualTo(1);
        assertThat(summary.mergeCandidateClusters()).hasSize(1);
        assertThat(summary.mergeCandidateClusters().get(0).clusterId()).isEqualTo("sim-1");
        assertThat(summary.mergeCandidateClusters().get(0).chunkIds()).containsExactly("chunk-1", "chunk-2");
        assertThat(summary.requestedDocumentType()).isEqualTo("auto");
        assertThat(summary.detectedDocumentType()).isEqualTo("policy");
        assertThat(summary.documentTypeConfidence()).isEqualTo(0.91d);
        assertThat(summary.documentTypeReason()).isEqualTo("policy signals");
        assertThat(summary.blockifyProfile()).isEqualTo("policy-v1");
        assertThat(summary.ideaBlockSchemaVersion()).isEqualTo("ideablock-policy-v1");
        assertThat(summary.typedFieldCounts()).containsEntry("articleNo", 2).containsEntry("obligation", 1);
        assertThat(summary.typedFieldCoverage()).containsEntry("articleNo", 1.0d).containsEntry("obligation", 0.5d);
        assertThat(summary.rejectedReasons()).containsExactly("TABLE_SECTION");
        assertThat(summary.missingSourceBlocks()).isEmpty();
        assertThat(summary.samples()).hasSize(2);
        assertThat(summary.samples().get(0).mergeCandidate()).isTrue();
        assertThat(summary.samples().get(0).similarityClusterId()).isEqualTo("sim-1");
        assertThat(summary.samples().get(0).actualChunkingStrategy()).isEqualTo("blockify");
        assertThat(summary.samples().get(0).schemaVersion()).isEqualTo("blockify-metadata-v1");
        assertThat(summary.samples().get(0).typedFields()).isEqualTo(Map.of(
                "articleNo", "제1조",
                "obligation", "복무 기준을 준수해야 한다."));
    }

    @Test
    void ideaBlockSummaryFallsBackToIndexedChunksAfterStageIsDeleted() {
        RagChunkStage indexed = new RagChunkStage("attachment", "42", "document-1", 0, "chunk-1", "idea block",
                Map.ofEntries(
                        Map.entry("chunkType", "ideaBlock"),
                        Map.entry("actualChunkingStrategy", "blockify"),
                        Map.entry("requestedDocumentType", "auto"),
                        Map.entry("detectedDocumentType", "narrative"),
                        Map.entry("blockifyProfile", "narrative-v1"),
                        Map.entry("ideaBlockSchemaVersion", "blockify-narrative-v1"),
                        Map.entry("sourceBlockRange", Map.of("start", 1, "end", 1)),
                        Map.entry("ideaBlockSourceBlockTargetCount", 1),
                        Map.entry("ideaBlockSourceBlockCoveredCount", 1)),
                null);
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore() {
            @Override
            public java.util.List<RagChunkStage> findIndexedByObject(String objectType, String objectId,
                    String revisionId) {
                assertThat(revisionId).isEqualTo("revision-1");
                return java.util.List.of(indexed);
            }
        };
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class),
                provider(RagChunkStageStore.class, stageStore),
                mock(MarkdownRepository.class));

        MarkdownIdeaBlockSummary summary = adapter.ideaBlockSummary(revision());

        assertThat(summary.chunkCount()).isEqualTo(1);
        assertThat(summary.ideaBlockCount()).isEqualTo(1);
        assertThat(summary.coverage()).isEqualTo(1.0d);
        assertThat(summary.detectedDocumentType()).isEqualTo("narrative");
        assertThat(summary.blockifyProfile()).isEqualTo("narrative-v1");
    }

    @Test
    void reportsEmbeddingSimilarityClustersForMergeCandidates() {
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        EmbeddingPort embeddingPort = request -> new EmbeddingResponse(List.of(
                new EmbeddingVector("chunk-1", List.of(1.0d, 0.0d)),
                new EmbeddingVector("chunk-2", List.of(0.98d, 0.02d))));
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class),
                provider(RagChunkStageStore.class, stageStore),
                provider(EmbeddingPort.class, embeddingPort),
                mock(MarkdownRepository.class));
        stageStore.replace("attachment", "42", "document-1", List.of(
                new RagChunkStage("attachment", "42", "document-1", 0, "chunk-1", "idea block 1",
                        Map.ofEntries(
                                Map.entry("chunkType", "ideaBlock"),
                                Map.entry("actualChunkingStrategy", "blockify"),
                                Map.entry("criticalQuestion", "연차휴가 신청 승인 기준은 무엇인가?"),
                                Map.entry("trustedAnswer", "연차휴가는 사전에 신청하여 승인을 받은 뒤 사용할 수 있다."),
                                Map.entry("keywords", List.of("연차휴가", "승인")),
                                Map.entry("entityName", "연차휴가"),
                                Map.entry("entityType", "policy"),
                                Map.entry("ideaBlockMergeCandidate", true),
                                Map.entry("ideaBlockSimilarityClusterId", "sim-1"),
                                Map.entry("ideaBlockSimilarityClusterSize", 2),
                                Map.entry("ideaBlockSimilarityMaxScore", 0.91d),
                                Map.entry("sourceBlockRange", Map.of("start", 1, "end", 1))),
                        null),
                new RagChunkStage("attachment", "42", "document-1", 1, "chunk-2", "idea block 2",
                        Map.ofEntries(
                                Map.entry("chunkType", "ideaBlock"),
                                Map.entry("actualChunkingStrategy", "blockify"),
                                Map.entry("criticalQuestion", "휴가 신청은 어떻게 승인되는가?"),
                                Map.entry("trustedAnswer", "휴가는 사전에 신청하고 승인을 받은 뒤 사용할 수 있다."),
                                Map.entry("keywords", List.of("휴가", "승인")),
                                Map.entry("entityName", "휴가"),
                                Map.entry("entityType", "policy"),
                                Map.entry("ideaBlockMergeCandidate", true),
                                Map.entry("ideaBlockSimilarityClusterId", "sim-1"),
                                Map.entry("ideaBlockSimilarityClusterSize", 2),
                                Map.entry("ideaBlockSimilarityMaxScore", 0.91d),
                                Map.entry("sourceBlockRange", Map.of("start", 2, "end", 2))),
                        null)));

        MarkdownIdeaBlockSummary summary = adapter.ideaBlockSummary(revision());

        assertThat(summary.embeddingSimilarityThreshold()).isEqualTo(0.90d);
        assertThat(summary.embeddingMergeCandidateCount()).isEqualTo(2);
        assertThat(summary.embeddingSimilarityClusterCount()).isEqualTo(1);
        assertThat(summary.embeddingCandidateClusters()).hasSize(1);
        assertThat(summary.embeddingCandidateClusters().get(0).clusterId()).isEqualTo("emb-1");
        assertThat(summary.embeddingCandidateClusters().get(0).chunkIds()).containsExactly("chunk-1", "chunk-2");
        assertThat(summary.samples()).hasSize(2);
        assertThat(summary.samples().get(0).embeddingMergeCandidate()).isTrue();
        assertThat(summary.samples().get(0).embeddingSimilarityClusterId()).isEqualTo("emb-1");
    }

    @Test
    void skipsEmbeddingSimilarityClustersWhenEntitiesConflict() {
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        EmbeddingPort embeddingPort = request -> new EmbeddingResponse(List.of(
                new EmbeddingVector("chunk-1", List.of(1.0d, 0.0d)),
                new EmbeddingVector("chunk-2", List.of(0.99d, 0.01d))));
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class),
                provider(RagChunkStageStore.class, stageStore),
                provider(EmbeddingPort.class, embeddingPort),
                mock(MarkdownRepository.class));
        stageStore.replace("attachment", "42", "document-1", List.of(
                new RagChunkStage("attachment", "42", "document-1", 0, "chunk-1", "idea block 1",
                        Map.ofEntries(
                                Map.entry("chunkType", "ideaBlock"),
                                Map.entry("actualChunkingStrategy", "blockify"),
                                Map.entry("criticalQuestion", "연차휴가 승인 기준은 무엇인가?"),
                                Map.entry("trustedAnswer", "연차휴가는 사전에 신청하여 승인을 받은 뒤 사용할 수 있다."),
                                Map.entry("entityName", "연차휴가"),
                                Map.entry("entityType", "policy"),
                                Map.entry("ideaBlockMergeCandidate", true)),
                        null),
                new RagChunkStage("attachment", "42", "document-1", 1, "chunk-2", "idea block 2",
                        Map.ofEntries(
                                Map.entry("chunkType", "ideaBlock"),
                                Map.entry("actualChunkingStrategy", "blockify"),
                                Map.entry("criticalQuestion", "징계 승인 기준은 무엇인가?"),
                                Map.entry("trustedAnswer", "징계는 사실 관계를 확인한 뒤 승인된 기준에 따라 조치한다."),
                                Map.entry("entityName", "징계"),
                                Map.entry("entityType", "policy"),
                                Map.entry("ideaBlockMergeCandidate", true)),
                        null)));

        MarkdownIdeaBlockSummary summary = adapter.ideaBlockSummary(revision());

        assertThat(summary.embeddingMergeCandidateCount()).isZero();
        assertThat(summary.embeddingSimilarityClusterCount()).isZero();
    }

    @Test
    void createsReadOnlyMergePreviewFromStagedIdeaBlockCandidates() {
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class),
                provider(RagChunkStageStore.class, stageStore),
                mock(MarkdownRepository.class));
        stageStore.replace("attachment", "42", "document-1", List.of(
                ideaBlockStage("chunk-1", "연차휴가 신청 기준은 무엇인가?", "연차휴가는 사전에 신청하여 승인 후 사용한다.", 1),
                ideaBlockStage("chunk-2", "휴가 신청 승인 절차는 무엇인가?", "휴가는 사전에 신청하여 승인 후 사용한다.", 2)));

        MarkdownIdeaBlockMergePreview preview = adapter.ideaBlockMergePreview(
                revision(),
                new MarkdownIdeaBlockMergePreviewOptions("sim-1", false, null, null, 5));

        assertThat(preview.clusters()).hasSize(1);
        assertThat(preview.llmUsed()).isFalse();
        assertThat(preview.clusters().get(0).clusterId()).isEqualTo("sim-1");
        assertThat(preview.clusters().get(0).clusterType()).isEqualTo("lexical");
        assertThat(preview.clusters().get(0).status()).isEqualTo("PREVIEW");
        assertThat(preview.clusters().get(0).reason()).isEqualTo("LLM_NOT_CONFIGURED");
        assertThat(preview.clusters().get(0).chunkIds()).containsExactly("chunk-1", "chunk-2");
        assertThat(preview.clusters().get(0).trustedAnswer()).contains("연차휴가는 사전에 신청");
        assertThat(preview.clusters().get(0).planId()).isEqualTo("merge-plan:lexical:sim-1");
        assertThat(preview.clusters().get(0).planFingerprint()).startsWith("sha256:");
        assertThat(preview.clusters().get(0).applicable()).isFalse();
        assertThat(preview.clusters().get(0).validationWarnings()).containsExactly("LLM_NOT_CONFIGURED");
        assertThat(preview.clusters().get(0).mergedFromChunkIds()).containsExactly("chunk-1", "chunk-2");
    }

    @Test
    void usesLlmForMergePreviewWhenProviderIsAvailable() {
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        ChatPort chatPort = request -> new ChatResponse(
                List.of(ChatMessage.assistant("## Critical Question\n휴가 신청 기준은 무엇인가?\n\n## Trusted Answer\n휴가는 사전 신청 및 승인 후 사용할 수 있다.")),
                "test-model",
                Map.of());
        AiProviderRegistry registry = new AiProviderRegistry("test",
                Map.of("test", chatPort),
                Map.of());
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class),
                provider(RagChunkStageStore.class, stageStore),
                provider(EmbeddingPort.class),
                provider(AiProviderRegistry.class, registry),
                mock(MarkdownRepository.class));
        stageStore.replace("attachment", "42", "document-1", List.of(
                ideaBlockStage("chunk-1", "연차휴가 신청 기준은 무엇인가?", "연차휴가는 사전에 신청하여 승인 후 사용한다.", 1),
                ideaBlockStage("chunk-2", "휴가 신청 승인 절차는 무엇인가?", "휴가는 사전에 신청하여 승인 후 사용한다.", 2)));

        MarkdownIdeaBlockMergePreview preview = adapter.ideaBlockMergePreview(
                revision(),
                new MarkdownIdeaBlockMergePreviewOptions("sim-1", false, "test", "test-model", 5));

        assertThat(preview.llmUsed()).isTrue();
        assertThat(preview.llmProvider()).isEqualTo("test");
        assertThat(preview.llmModel()).isEqualTo("test-model");
        assertThat(preview.clusters()).hasSize(1);
        assertThat(preview.clusters().get(0).status()).isEqualTo("PREVIEW");
        assertThat(preview.clusters().get(0).reason()).isEqualTo("LLM_PREVIEW");
        assertThat(preview.clusters().get(0).previewText()).contains("Trusted Answer");
        assertThat(preview.clusters().get(0).planId()).isEqualTo("merge-plan:lexical:sim-1");
        assertThat(preview.clusters().get(0).planFingerprint()).startsWith("sha256:");
        assertThat(preview.clusters().get(0).applicable()).isTrue();
        assertThat(preview.clusters().get(0).validationWarnings()).isEmpty();
        assertThat(preview.clusters().get(0).mergedFromChunkIds()).containsExactly("chunk-1", "chunk-2");
    }

    @Test
    void rejectsLlmMergePreviewWhenRequiredSectionsAreMissing() {
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        ChatPort chatPort = request -> new ChatResponse(
                List.of(ChatMessage.assistant("휴가는 사전 신청 및 승인 후 사용할 수 있다.")),
                "test-model",
                Map.of());
        AiProviderRegistry registry = new AiProviderRegistry("test",
                Map.of("test", chatPort),
                Map.of());
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class),
                provider(RagChunkStageStore.class, stageStore),
                provider(EmbeddingPort.class),
                provider(AiProviderRegistry.class, registry),
                mock(MarkdownRepository.class));
        stageStore.replace("attachment", "42", "document-1", List.of(
                ideaBlockStage("chunk-1", "연차휴가 신청 기준은 무엇인가?", "연차휴가는 사전에 신청하여 승인 후 사용한다.", 1),
                ideaBlockStage("chunk-2", "휴가 신청 승인 절차는 무엇인가?", "휴가는 사전에 신청하여 승인 후 사용한다.", 2)));

        MarkdownIdeaBlockMergePreview preview = adapter.ideaBlockMergePreview(
                revision(),
                new MarkdownIdeaBlockMergePreviewOptions("sim-1", false, "test", "test-model", 5));

        assertThat(preview.clusters()).hasSize(1);
        assertThat(preview.clusters().get(0).status()).isEqualTo("REJECTED");
        assertThat(preview.clusters().get(0).reason()).isEqualTo("LLM_PREVIEW_INVALID");
        assertThat(preview.clusters().get(0).applicable()).isFalse();
        assertThat(preview.clusters().get(0).validationWarnings())
                .contains("MISSING_CRITICAL_QUESTION_SECTION", "MISSING_TRUSTED_ANSWER_SECTION");
    }

    @Test
    void appliesLlmMergePreviewToStagedIdeaBlockChunks() {
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        ChatPort chatPort = request -> new ChatResponse(
                List.of(ChatMessage.assistant("## Critical Question\n휴가 신청 기준은 무엇인가?\n\n## Trusted Answer\n휴가는 사전 신청 및 승인 후 사용할 수 있다.")),
                "test-model",
                Map.of());
        AiProviderRegistry registry = new AiProviderRegistry("test",
                Map.of("test", chatPort),
                Map.of());
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class),
                provider(RagChunkStageStore.class, stageStore),
                provider(EmbeddingPort.class),
                provider(AiProviderRegistry.class, registry),
                mock(MarkdownRepository.class));
        stageStore.replace("attachment", "42", "document-1", List.of(
                ideaBlockStage("chunk-1", "연차휴가 신청 기준은 무엇인가?", "연차휴가는 사전에 신청하여 승인 후 사용한다.", 1),
                ideaBlockStage("chunk-2", "휴가 신청 승인 절차는 무엇인가?", "휴가는 사전에 신청하여 승인 후 사용한다.", 2),
                ideaBlockStage("chunk-3", "병가 신청 기준은 무엇인가?", "병가는 증빙을 제출하여 신청한다.", 3, "sim-2")));
        MarkdownIdeaBlockMergePreview preview = adapter.ideaBlockMergePreview(
                revision(),
                new MarkdownIdeaBlockMergePreviewOptions("sim-1", false, "test", "test-model", 5));

        MarkdownIdeaBlockMergeApplyResult result = adapter.ideaBlockMergeApply(
                revision(),
                new MarkdownIdeaBlockMergeApplyOptions(
                        new MarkdownIdeaBlockMergePreviewOptions("sim-1", false, "test", "test-model", 5),
                        preview.clusters().get(0).planFingerprint()));

        assertThat(result.beforeChunkCount()).isEqualTo(3);
        assertThat(result.afterChunkCount()).isEqualTo(2);
        assertThat(result.mergedFromChunkIds()).containsExactly("chunk-1", "chunk-2");
        List<RagChunkStage> stages = stageStore.findByObject("attachment", "42", "document-1");
        assertThat(stages).hasSize(2);
        assertThat(stages.get(0).chunkId()).startsWith("ideablock-merged-");
        assertThat(stages.get(0).metadata())
                .containsEntry("ideaBlockDistilled", true)
                .containsEntry("validationStatus", "DISTILLED")
                .containsEntry("ideaBlockDistillationFingerprint", preview.clusters().get(0).planFingerprint());
        assertThat(stages.get(0).metadata().get("ideaBlockDistilledFromChunkIds"))
                .isEqualTo(List.of("chunk-1", "chunk-2"));
        assertThat((List<?>) stages.get(0).metadata().get("ideaBlockDistillationSourceChunks")).hasSize(2);
        assertThat(stages.get(1).chunkId()).isEqualTo("chunk-3");
        assertThat(stages.get(0).chunkIndex()).isZero();
        assertThat(stages.get(1).chunkIndex()).isEqualTo(1);
    }

    @Test
    void undoesMergedIdeaBlockStageFromStoredSourceSnapshots() {
        RagChunkStageStore stageStore = new InMemoryRagChunkStageStore();
        ChatPort chatPort = request -> new ChatResponse(
                List.of(ChatMessage.assistant("## Critical Question\n휴가 신청 기준은 무엇인가?\n\n## Trusted Answer\n휴가는 사전 신청 및 승인 후 사용할 수 있다.")),
                "test-model",
                Map.of());
        AiProviderRegistry registry = new AiProviderRegistry("test",
                Map.of("test", chatPort),
                Map.of());
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class),
                provider(RagChunkStageStore.class, stageStore),
                provider(EmbeddingPort.class),
                provider(AiProviderRegistry.class, registry),
                mock(MarkdownRepository.class));
        stageStore.replace("attachment", "42", "document-1", List.of(
                ideaBlockStage("chunk-1", "연차휴가 신청 기준은 무엇인가?", "연차휴가는 사전에 신청하여 승인 후 사용한다.", 1),
                ideaBlockStage("chunk-2", "휴가 신청 승인 절차는 무엇인가?", "휴가는 사전에 신청하여 승인 후 사용한다.", 2),
                ideaBlockStage("chunk-3", "병가 신청 기준은 무엇인가?", "병가는 증빙을 제출하여 신청한다.", 3, "sim-2")));
        MarkdownIdeaBlockMergePreview preview = adapter.ideaBlockMergePreview(
                revision(),
                new MarkdownIdeaBlockMergePreviewOptions("sim-1", false, "test", "test-model", 5));
        MarkdownIdeaBlockMergeApplyResult applied = adapter.ideaBlockMergeApply(
                revision(),
                new MarkdownIdeaBlockMergeApplyOptions(
                        new MarkdownIdeaBlockMergePreviewOptions("sim-1", false, "test", "test-model", 5),
                        preview.clusters().get(0).planFingerprint()));

        MarkdownIdeaBlockMergeUndoResult result = adapter.ideaBlockMergeUndo(
                revision(),
                new MarkdownIdeaBlockMergeUndoOptions(applied.mergedChunkId(), null));

        assertThat(result.beforeChunkCount()).isEqualTo(2);
        assertThat(result.afterChunkCount()).isEqualTo(3);
        assertThat(result.restoredChunkIds()).containsExactly("chunk-1", "chunk-2");
        List<RagChunkStage> stages = stageStore.findByObject("attachment", "42", "document-1");
        assertThat(stages).extracting(RagChunkStage::chunkId)
                .containsExactly("chunk-1", "chunk-2", "chunk-3");
        assertThat(stages).extracting(RagChunkStage::chunkIndex)
                .containsExactly(0, 1, 2);
    }

    @Test
    void failsPipelineWhenRagJobDoesNotComplete() {
        RagIndexJobService ragJobs = mock(RagIndexJobService.class);
        RagIndexJob pending = job(RagIndexJobStatus.PENDING, null);
        RagIndexJob failed = job(RagIndexJobStatus.FAILED, "embedding failed");
        when(ragJobs.createJob(any(RagIndexJobCreateRequest.class))).thenReturn(pending);
        when(ragJobs.createJob(any(RagIndexJobCreateRequest.class), any(RagIndexJobSourceRequest.class)))
                .thenReturn(pending);
        when(ragJobs.startJob("rag-job-1")).thenReturn(failed);
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class, ragJobs),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class),
                provider(RagChunkStageStore.class, preparedStageStore()),
                mock(MarkdownRepository.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                adapter.process(revision(), new MarkdownPipelineOptions(true, true, false),
                        studio.one.platform.markdown.domain.MarkdownPipelineStage.RAG_INDEX, stage -> {
                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("embedding failed");
    }

    private RagChunkStageStore preparedStageStore() {
        InMemoryRagChunkStageStore store = new InMemoryRagChunkStageStore();
        store.replace("attachment", "42", "document-1", List.of(new RagChunkStage(
                "attachment", "42", "document-1", 0, "chunk-1", "prepared markdown chunk",
                Map.of("strategy", "recursive", "chunkOrder", 0), null)));
        return store;
    }

    private RagIndexJobService completedJobService() {
        RagIndexJobService service = mock(RagIndexJobService.class);
        RagIndexJob job = job(RagIndexJobStatus.PENDING, null);
        when(service.createJob(any(RagIndexJobCreateRequest.class))).thenReturn(job);
        when(service.createJob(any(RagIndexJobCreateRequest.class), any(RagIndexJobSourceRequest.class)))
                .thenReturn(job);
        when(service.startJob("rag-job-1")).thenReturn(job(RagIndexJobStatus.SUCCEEDED, null));
        return service;
    }

    private RagIndexJob job(RagIndexJobStatus status, String errorMessage) {
        Instant now = Instant.parse("2026-06-13T00:00:00Z");
        return new RagIndexJob(
                "rag-job-1", "attachment", "42", "document-1", "markdown-revision", "sample.txt",
                status, null, 0, 0, 0, 0, errorMessage, now,
                status == RagIndexJobStatus.PENDING ? null : now,
                status == RagIndexJobStatus.PENDING ? null : now,
                status == RagIndexJobStatus.PENDING ? null : 0L);
    }

    private MarkdownRevision revision() {
        return revision("# Content");
    }

    private MarkdownRevision revision(String markdownText) {
        Instant now = Instant.parse("2026-06-13T00:00:00Z");
        return new MarkdownRevision(
                "revision-1", "document-1", 42L, null, null,
                "TEXTRACT", "native", "{}", "options-hash", "source-hash", "content-hash",
                markdownText, "sample.txt", "txt", "2001", "7",
                MarkdownRevisionStatus.COMPLETED, null, null, now, now, now, now);
    }

    private Map<String, Object> blockifyMetadata() {
        return Map.of(
                "schemaVersion", "blockify-metadata-v1",
                "requestedChunkingStrategy", "blockify",
                "actualChunkingStrategy", "blockify",
                "validationStatus", "RULE_VALIDATED",
                "blockifyFingerprint", "sha256:test",
                "question", "복무 기준은 무엇인가?",
                "answer", "복무 기준을 정하는 것을 목적으로 한다.",
                "sourceEvidence", List.of(Map.of("text", "복무 기준을 정하는 것을 목적으로 한다.")));
    }

    private RagChunkStage ideaBlockStage(String chunkId, String question, String answer, int order) {
        return ideaBlockStage(chunkId, question, answer, order, "sim-1");
    }

    private RagChunkStage ideaBlockStage(String chunkId, String question, String answer, int order, String clusterId) {
        return new RagChunkStage("attachment", "42", "document-1", order - 1, chunkId, answer,
                Map.ofEntries(
                        Map.entry("chunkType", "ideaBlock"),
                        Map.entry("actualChunkingStrategy", "blockify"),
                        Map.entry("criticalQuestion", question),
                        Map.entry("trustedAnswer", answer),
                        Map.entry("keywords", List.of("휴가", "승인")),
                        Map.entry("tags", List.of("휴가")),
                        Map.entry("entityName", "휴가"),
                        Map.entry("entityType", "policy"),
                        Map.entry("sourceEvidence", List.of(Map.of("text", answer))),
                        Map.entry("sourceBlockRange", Map.of("start", order, "end", order)),
                        Map.entry("ideaBlockMergeCandidate", true),
                        Map.entry("ideaBlockSimilarityClusterId", clusterId),
                        Map.entry("ideaBlockSimilarityClusterSize", 2),
                        Map.entry("ideaBlockSimilarityMaxScore", 0.91d)),
                null);
    }

    @SafeVarargs
    private final <T> org.springframework.beans.factory.ObjectProvider<T> provider(Class<T> type, T... values) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        for (int index = 0; index < values.length; index++) {
            factory.registerSingleton(type.getName() + index, values[index]);
        }
        return factory.getBeanProvider(type);
    }
}
