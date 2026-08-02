package studio.one.application.webknowledge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeRevisionEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlRunEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeRevisionJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;
import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.service.pipeline.RagEmbeddingProfileResolver;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.service.pipeline.ResolvedRagEmbedding;
import tools.jackson.databind.json.JsonMapper;

class DefaultWebKnowledgeSourceServiceTest {

    @Test
    void rejectsUnknownEmbeddingDeploymentBeforePersistingSource() {
        WebKnowledgeSourceJpaRepository sources = mock(WebKnowledgeSourceJpaRepository.class);
        WebKnowledgeRevisionJpaRepository revisions = mock(WebKnowledgeRevisionJpaRepository.class);
        RagIndexJobService jobs = mock(RagIndexJobService.class);
        RagPipelineService ragPipeline = mock(RagPipelineService.class);
        RagEmbeddingProfileResolver embeddingResolver = mock(RagEmbeddingProfileResolver.class);
        when(sources.findByWorkspaceIdAndNormalizedUrlHashAndEmbeddingDeploymentIdAndArchivedFalse(
                any(), any(), any())).thenReturn(Optional.empty());
        when(embeddingResolver.resolve(any())).thenThrow(new IllegalArgumentException("unknown deployment"));
        DefaultWebKnowledgeSourceService service = new DefaultWebKnowledgeSourceService(
                sources,
                revisions,
                jobs,
                ragPipeline,
                embeddingResolver,
                new WebKnowledgeContentSanitizer(true),
                Runnable::run);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(
                        2L,
                        new WebKnowledgeSourceCreateCommand(
                                2L,
                                "https://example.org/article",
                                "reference",
                                "unknown-deployment",
                                "tester"),
                        "tester"));

        verify(sources, never()).save(any());
        verify(revisions, never()).save(any());
    }

    @Test
    void cancelsStalePendingRevisionWithoutCancellingCompletedJob() {
        WebKnowledgeSourceJpaRepository sources = mock(WebKnowledgeSourceJpaRepository.class);
        WebKnowledgeRevisionJpaRepository revisions = mock(WebKnowledgeRevisionJpaRepository.class);
        RagIndexJobService jobs = mock(RagIndexJobService.class);
        RagPipelineService ragPipeline = mock(RagPipelineService.class);
        RagEmbeddingProfileResolver embeddingResolver = mock(RagEmbeddingProfileResolver.class);
        WebKnowledgeSourceEntity source = source();
        WebKnowledgeRevisionEntity revision = new WebKnowledgeRevisionEntity(
                "wrev-1", source.sourceId(), Instant.now());
        revision.job("job-1", Instant.now());
        RagIndexJob completedJob = mock(RagIndexJob.class);
        when(completedJob.status()).thenReturn(RagIndexJobStatus.SUCCEEDED);
        when(sources.findBySourceIdAndWorkspaceIdAndArchivedFalse(source.sourceId(), 2L))
                .thenReturn(Optional.of(source));
        when(revisions.findBySourceIdOrderByCreatedAtDesc(source.sourceId()))
                .thenReturn(List.of(revision));
        when(jobs.getJob("job-1")).thenReturn(Optional.of(completedJob));

        DefaultWebKnowledgeSourceService service = service(
                sources, revisions, jobs, ragPipeline, embeddingResolver);

        WebKnowledgeSourceView result = service.cancel(2L, source.sourceId());

        assertEquals("CANCELLED", result.status());
        assertEquals("CANCELLED", result.revisionStatus());
        verify(jobs, never()).cancelJob("job-1");
        verify(sources).save(source);
        verify(revisions).save(revision);
    }

    @Test
    void siteModeCreatesDurableRunAndDispatchesCoordinator() {
        WebKnowledgeSourceJpaRepository sources = mock(WebKnowledgeSourceJpaRepository.class);
        WebKnowledgeRevisionJpaRepository revisions = mock(WebKnowledgeRevisionJpaRepository.class);
        RagIndexJobService jobs = mock(RagIndexJobService.class);
        RagPipelineService ragPipeline = mock(RagPipelineService.class);
        RagEmbeddingProfileResolver embeddingResolver = mock(RagEmbeddingProfileResolver.class);
        WebKnowledgeSiteCrawlCoordinator coordinator = mock(WebKnowledgeSiteCrawlCoordinator.class);
        ResolvedRagEmbedding embedding = mock(ResolvedRagEmbedding.class);
        WebKnowledgeCrawlRunEntity run = mock(WebKnowledgeCrawlRunEntity.class);
        when(sources.findByWorkspaceIdAndNormalizedUrlHashAndEmbeddingDeploymentIdAndArchivedFalse(
                any(), any(), any())).thenReturn(Optional.empty());
        when(embeddingResolver.resolve(any())).thenReturn(embedding);
        when(embedding.embeddingSpaceId()).thenReturn("space-1");
        when(run.runId()).thenReturn("wrun-1");
        when(coordinator.createRun(any(), any(), any(), any())).thenReturn(run);
        when(coordinator.latestRun(any(), any())).thenReturn(Optional.empty());

        DefaultWebKnowledgeSourceService service = new DefaultWebKnowledgeSourceService(
                sources,
                revisions,
                jobs,
                ragPipeline,
                embeddingResolver,
                new WebKnowledgeContentSanitizer(true),
                Runnable::run,
                new WebCrawlPolicyResolver(WebCrawlPolicyResolver.Limits.defaults()),
                coordinator,
                null,
                JsonMapper.builder().build(),
                true);

        WebKnowledgeSourceView created = service.create(
                2L,
                new WebKnowledgeSourceCreateCommand(
                        2L,
                        "https://example.org/",
                        "site",
                        "embedding-default",
                        "tester",
                        "SITE",
                        WebCrawlPolicyInput.defaults()),
                "tester");

        assertEquals("SITE", created.collectionMode());
        verify(coordinator).createRun(any(), any(), any(), any());
        verify(coordinator).execute("wrun-1");
        verify(jobs, never()).createJob(any(), any());
        verify(revisions, never()).save(any());
    }

    private static DefaultWebKnowledgeSourceService service(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeRevisionJpaRepository revisions,
            RagIndexJobService jobs,
            RagPipelineService ragPipeline,
            RagEmbeddingProfileResolver embeddingResolver) {
        return new DefaultWebKnowledgeSourceService(
                sources,
                revisions,
                jobs,
                ragPipeline,
                embeddingResolver,
                new WebKnowledgeContentSanitizer(true),
                Runnable::run);
    }

    private static WebKnowledgeSourceEntity source() {
        return new WebKnowledgeSourceEntity(
                "wsrc-1",
                2L,
                "https://example.org/article",
                "https://example.org/article",
                "hash",
                "example.org",
                "reference",
                "embedding-default",
                "space-1",
                "tester",
                Instant.now());
    }
}
