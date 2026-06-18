package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.service.pipeline.RagChunkStageStore;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownRevision;
import studio.one.platform.markdown.domain.MarkdownRevisionStatus;
import studio.one.platform.skillgraph.application.usecase.SkillRagExtractionJobService;

class MarkdownDownstreamPipelineAdapterTest {

    @Test
    void forwardsChunkingAndEmbeddingSelections() {
        RagIndexJobService ragJobs = completedJobService();
        ChunkingOrchestrator chunking = mock(ChunkingOrchestrator.class);
        RagChunkStageStore stageStore = mock(RagChunkStageStore.class);
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
                repository);
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
        verify(ragJobs).createJob(request.capture());
        assertThat(request.getValue().sourceType()).isEqualTo("markdown-revision");
        assertThat(request.getValue().objectType()).isEqualTo("attachment");
        assertThat(request.getValue().objectId()).isEqualTo("42");
        assertThat(request.getValue().indexRequest().embeddingProvider()).isEqualTo("google");
        assertThat(request.getValue().indexRequest().embeddingModel()).isEqualTo("gemini-embedding-001");
        assertThat(request.getValue().indexRequest().embeddingDimension()).isEqualTo(768);
        assertThat(request.getValue().indexRequest().useLlmKeywordExtraction()).isTrue();
        assertThat(request.getValue().indexRequest().metadata())
                .containsEntry("markdownRevisionId", "revision-1")
                .containsEntry("strategy", "fixed-size");
        verify(ragJobs).startJob("rag-job-1");
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
        RagChunkStageStore stageStore = mock(RagChunkStageStore.class);
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
    void failsPipelineWhenRagJobDoesNotComplete() {
        RagIndexJobService ragJobs = mock(RagIndexJobService.class);
        RagIndexJob pending = job(RagIndexJobStatus.PENDING, null);
        RagIndexJob failed = job(RagIndexJobStatus.FAILED, "embedding failed");
        when(ragJobs.createJob(any(RagIndexJobCreateRequest.class))).thenReturn(pending);
        when(ragJobs.startJob("rag-job-1")).thenReturn(failed);
        MarkdownDownstreamPipelineAdapter adapter = new MarkdownDownstreamPipelineAdapter(
                provider(RagIndexJobService.class, ragJobs),
                provider(SkillRagExtractionJobService.class),
                provider(ChunkingOrchestrator.class),
                provider(RagChunkStageStore.class),
                mock(MarkdownRepository.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                adapter.process(revision(), new MarkdownPipelineOptions(true, true, false),
                        studio.one.platform.markdown.domain.MarkdownPipelineStage.RAG_INDEX, stage -> {
                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("embedding failed");
    }

    private RagIndexJobService completedJobService() {
        RagIndexJobService service = mock(RagIndexJobService.class);
        RagIndexJob job = job(RagIndexJobStatus.PENDING, null);
        when(service.createJob(any(RagIndexJobCreateRequest.class))).thenReturn(job);
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
        Instant now = Instant.parse("2026-06-13T00:00:00Z");
        return new MarkdownRevision(
                "revision-1", "document-1", 42L, null, null,
                "TEXTRACT", "native", "{}", "options-hash", "source-hash", "content-hash",
                "# Content", "sample.txt", "txt", "2001", "7",
                MarkdownRevisionStatus.COMPLETED, null, null, now, now, now, now);
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
