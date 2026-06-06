package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.objecttype.application.command.ValidateUploadCommand;
import studio.one.platform.objecttype.application.result.ObjectTypeDefinition;
import studio.one.platform.objecttype.application.result.ObjectTypeView;
import studio.one.platform.objecttype.application.result.ValidateUploadResult;
import studio.one.platform.objecttype.application.usecase.ObjectTypeRuntimeService;
import studio.one.platform.skillgraph.application.command.SkillExtractionCommand;
import studio.one.platform.skillgraph.application.result.ResolvedRagChunk;
import studio.one.platform.skillgraph.application.result.SkillExtractionResult;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionItemStatus;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobItem;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobStatus;
import studio.one.platform.skillgraph.application.service.DefaultSkillRagExtractionJobService;
import studio.one.platform.skillgraph.application.service.SkillRagExtractionJobSettings;
import studio.one.platform.skillgraph.application.usecase.SkillExtractionService;
import studio.one.platform.skillgraph.application.usecase.SkillGraphRagChunkResolver;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillRagExtractionJobStore;

class DefaultSkillRagExtractionJobServiceTest {

    @Test
    void processesAllChunksByPageWithoutLoadingWholeDocument() {
        InMemorySkillRagExtractionJobStore store = new InMemorySkillRagExtractionJobStore();
        PagingResolver resolver = new PagingResolver(List.of(
                chunk("doc-1", "chunk-1", "Spring Boot"),
                chunk("doc-1", "chunk-2", "Kubernetes"),
                chunk("doc-1", "chunk-3", "PostgreSQL")));
        DefaultSkillRagExtractionJobService service = new DefaultSkillRagExtractionJobService(
                new CountingExtractionService(),
                resolver,
                store,
                Runnable::run,
                new SkillRagExtractionJobSettings(2, 10, 1_000_000));

        var submitted = service.submitAllChunks("attachment", "42", null);
        var job = service.getJob(submitted.jobId());

        assertEquals(SkillRagExtractionJobStatus.COMPLETED, job.status());
        assertEquals(3, job.totalChunks());
        assertEquals(3, job.processedChunks());
        assertEquals(3, job.succeededChunks());
        assertEquals(List.of(0, 2), resolver.offsets);
    }

    @Test
    void doesNotMarkJobFailedWhenWorkerLosesLease() {
        InMemorySkillRagExtractionJobStore store = new InMemorySkillRagExtractionJobStore() {
            @Override
            public synchronized boolean renewLease(
                    String jobId,
                    String owner,
                    Instant now,
                    Duration leaseDuration) {
                return false;
            }
        };
        DefaultSkillRagExtractionJobService service = new DefaultSkillRagExtractionJobService(
                new CountingExtractionService(),
                new PagingResolver(List.of(chunk("doc-1", "chunk-1", "Spring Boot"))),
                store,
                Runnable::run,
                new SkillRagExtractionJobSettings(20, 10, 1_000_000));

        var submitted = service.submitAllChunks("attachment", "42", null);
        var job = store.findJob(submitted.jobId()).orElseThrow();

        assertEquals(SkillRagExtractionJobStatus.RUNNING, job.status());
        assertEquals(1, job.processedChunks());
        assertNull(job.error());
    }

    @Test
    void processesObjectTypeScopedChunksWithoutObjectId() {
        InMemorySkillRagExtractionJobStore store = new InMemorySkillRagExtractionJobStore();
        PagingResolver resolver = new PagingResolver(List.of(
                chunk("doc-1", "42", "chunk-1", "Spring Boot"),
                chunk("doc-2", "43", "chunk-2", "Kubernetes")));
        DefaultSkillRagExtractionJobService service = new DefaultSkillRagExtractionJobService(
                new CountingExtractionService(),
                resolver,
                store,
                Runnable::run,
                new SkillRagExtractionJobSettings(2, 10, 1_000_000));

        var submitted = service.submitAllChunks("attachment", null, null);
        var job = service.getJob(submitted.jobId());

        assertEquals(SkillRagExtractionJobStatus.COMPLETED, job.status());
        assertNull(job.objectId());
        assertEquals(Arrays.asList((String) null), resolver.objectIds);
        var items = service.listItems(job.jobId(), 0, 10);
        assertEquals("doc-1", items.get(0).sourceId());
        assertEquals("doc-2", items.get(1).sourceId());
    }

    @Test
    void processesSelectedChunksWithinQueryAndPreservesOptions() {
        InMemorySkillRagExtractionJobStore store = new InMemorySkillRagExtractionJobStore();
        PagingResolver resolver = new PagingResolver(List.of(
                chunk("doc-1", "42", "chunk-1", "Spring Boot"),
                chunk("doc-1", "42", "chunk-2", "Kubernetes"),
                chunk("doc-2", "43", "chunk-3", "Spring Security")));
        DefaultSkillRagExtractionJobService service = new DefaultSkillRagExtractionJobService(
                new CountingExtractionService(),
                resolver,
                store,
                Runnable::run,
                new SkillRagExtractionJobSettings(2, 10, 1_000_000));

        var submitted = service.submit(
                "attachment",
                null,
                "spring",
                List.of("chunk-1", "chunk-3"),
                10,
                true,
                true,
                "kure",
                "nlpai-lab/KURE-v1",
                1024);
        var job = service.getJob(submitted.jobId());

        assertEquals(SkillRagExtractionJobStatus.COMPLETED, job.status());
        assertEquals("spring", job.query());
        assertEquals("SELECTED_CHUNKS", job.extractionMode());
        assertEquals(List.of("chunk-1", "chunk-3"), job.selectedChunkIds());
        assertEquals(true, job.excludeExtracted());
        assertEquals(true, job.generateEmbeddings());
        assertEquals("kure", job.embeddingProvider());
        assertEquals(2, job.totalChunks());
        assertEquals(List.of("chunk-1", "chunk-3"),
                service.listItems(job.jobId(), 0, 10).stream()
                        .map(SkillRagExtractionJobItem::chunkId)
                        .toList());
    }

    @Test
    void excludesSuccessfulChunksFromActivePreviousJobs() {
        InMemorySkillRagExtractionJobStore store = new InMemorySkillRagExtractionJobStore();
        Instant now = Instant.now();
        store.saveJob(new SkillRagExtractionJob(
                "previous-job",
                "attachment",
                null,
                null,
                SkillRagExtractionJobStatus.RUNNING,
                10,
                2,
                1,
                1,
                0,
                1,
                null,
                now,
                now));
        store.saveItem(new SkillRagExtractionJobItem(
                "previous-job",
                "chunk-1",
                "doc-1",
                "doc-1",
                "source-chunk-1",
                1,
                SkillRagExtractionItemStatus.SUCCEEDED,
                null,
                now,
                now));
        PagingResolver resolver = new PagingResolver(List.of(
                chunk("doc-1", "42", "chunk-1", "Spring Boot"),
                chunk("doc-2", "43", "chunk-2", "Kubernetes")));
        DefaultSkillRagExtractionJobService service = new DefaultSkillRagExtractionJobService(
                new CountingExtractionService(),
                resolver,
                store,
                Runnable::run,
                new SkillRagExtractionJobSettings(2, 10, 1_000_000));

        var submitted = service.submitAllChunks(
                "attachment", null, null, true, false, null, null, null);
        var job = service.getJob(submitted.jobId());

        assertEquals(SkillRagExtractionJobStatus.COMPLETED, job.status());
        assertEquals(1, job.totalChunks());
        assertEquals(1, job.processedChunks());
        assertEquals("chunk-2", service.listItems(job.jobId(), 0, 10).get(0).chunkId());
    }

    @Test
    void normalizesGenericAttachmentObjectTypeForRagChunkLookup() {
        InMemorySkillRagExtractionJobStore store = new InMemorySkillRagExtractionJobStore();
        PagingResolver resolver = new PagingResolver(List.of(
                chunk("doc-1", "chunk-1", "Spring Boot")));
        DefaultSkillRagExtractionJobService service = new DefaultSkillRagExtractionJobService(
                new CountingExtractionService(),
                resolver,
                store,
                Runnable::run,
                new SkillRagExtractionJobSettings(2, 10, 1_000_000),
                objectTypeService(2001, "attachment"));

        var submitted = service.submitAllChunks("2001", "42", null);
        var job = service.getJob(submitted.jobId());

        assertEquals(SkillRagExtractionJobStatus.COMPLETED, job.status());
        assertEquals("attachment", job.objectType());
        assertEquals("attachment", resolver.objectTypes.get(0));
        assertEquals("42", resolver.objectIds.get(0));
    }

    @Test
    void normalizesExistingGenericAttachmentJobForRagChunkLookup() {
        InMemorySkillRagExtractionJobStore store = new InMemorySkillRagExtractionJobStore();
        PagingResolver resolver = new PagingResolver(List.of(
                chunk("doc-1", "chunk-1", "Spring Boot")));
        DefaultSkillRagExtractionJobService service = new DefaultSkillRagExtractionJobService(
                new CountingExtractionService(),
                resolver,
                store,
                Runnable::run,
                new SkillRagExtractionJobSettings(2, 10, 1_000_000));
        Instant now = Instant.now();
        store.saveJob(new SkillRagExtractionJob(
                "job-legacy",
                "2001",
                "42",
                "doc-1",
                SkillRagExtractionJobStatus.FAILED,
                10,
                1,
                1,
                0,
                1,
                0,
                null,
                now,
                now));
        store.saveItem(new studio.one.platform.skillgraph.application.result.SkillRagExtractionJobItem(
                "job-legacy",
                "chunk-1",
                "doc-1",
                "doc-1",
                null,
                0,
                studio.one.platform.skillgraph.application.result.SkillRagExtractionItemStatus.FAILED,
                "failed",
                now,
                now));

        var retried = service.retryFailed("job-legacy");

        assertEquals(SkillRagExtractionJobStatus.COMPLETED, service.getJob(retried.jobId()).status());
        assertEquals("attachment", resolver.objectTypes.get(0));
        assertEquals("42", resolver.objectIds.get(0));
    }

    @Test
    void retryOrphanedJobPreservesOptionsAndSkipsSuccessfulChunks() {
        InMemorySkillRagExtractionJobStore store = new InMemorySkillRagExtractionJobStore();
        PagingResolver resolver = new PagingResolver(List.of(
                chunk("doc-1", "chunk-1", "Spring Boot"),
                chunk("doc-1", "chunk-2", "Kubernetes"),
                chunk("doc-1", "chunk-3", "PostgreSQL")));
        DefaultSkillRagExtractionJobService service = new DefaultSkillRagExtractionJobService(
                new CountingExtractionService(),
                resolver,
                store,
                Runnable::run,
                new SkillRagExtractionJobSettings(2, 10, 1_000_000));
        Instant now = Instant.now();
        store.saveJob(new SkillRagExtractionJob(
                "job-orphaned",
                "attachment",
                "42",
                "doc-1",
                SkillRagExtractionJobStatus.FAILED,
                10,
                3,
                1,
                1,
                0,
                1,
                "worker interrupted",
                true,
                true,
                "kure",
                "nlpai-lab/KURE-v1",
                1024,
                null,
                null,
                now,
                now));
        store.saveItem(new SkillRagExtractionJobItem(
                "job-orphaned",
                "chunk-1",
                "doc-1",
                "doc-1",
                "source-chunk-1",
                1,
                SkillRagExtractionItemStatus.SUCCEEDED,
                null,
                now,
                now));

        var retried = service.retryFailed("job-orphaned");
        var completed = service.getJob(retried.jobId());

        assertEquals(SkillRagExtractionJobStatus.COMPLETED, completed.status());
        assertEquals(true, completed.excludeExtracted());
        assertEquals(true, completed.generateEmbeddings());
        assertEquals("kure", completed.embeddingProvider());
        assertEquals(1024, completed.embeddingDimension());
        assertEquals(2, completed.totalChunks());
        assertEquals(2, completed.processedChunks());
        assertEquals(List.of("chunk-1", "chunk-2", "chunk-3"),
                service.listItems(completed.jobId(), 0, 10).stream()
                        .map(SkillRagExtractionJobItem::chunkId)
                        .toList());
    }

    @Test
    void recordsPartialJobWhenChunkFails() {
        InMemorySkillRagExtractionJobStore store = new InMemorySkillRagExtractionJobStore();
        DefaultSkillRagExtractionJobService service = new DefaultSkillRagExtractionJobService(
                command -> {
                    if ("chunk-2".equals(command.chunkId())) {
                        throw new IllegalArgumentException("bad chunk");
                    }
                    return new SkillExtractionResult("source-" + command.chunkId(), 1, List.of());
                },
                new PagingResolver(List.of(
                        chunk("doc-1", "chunk-1", "Spring Boot"),
                        chunk("doc-1", "chunk-2", "Kubernetes"))),
                store,
                Runnable::run,
                new SkillRagExtractionJobSettings(20, 10, 1_000_000));

        var submitted = service.submitAllChunks("attachment", "42", null);
        var job = service.getJob(submitted.jobId());

        assertEquals(SkillRagExtractionJobStatus.PARTIAL, job.status());
        assertEquals(1, job.succeededChunks());
        assertEquals(1, job.failedChunks());
        assertEquals("bad chunk", service.listItems(job.jobId(), 0, 10).get(1).error());
    }

    @Test
    void skipsChunkThatExceedsTextByteLimit() {
        InMemorySkillRagExtractionJobStore store = new InMemorySkillRagExtractionJobStore();
        DefaultSkillRagExtractionJobService service = new DefaultSkillRagExtractionJobService(
                new CountingExtractionService(),
                new PagingResolver(List.of(chunk("doc-1", "chunk-1", "Spring Boot"))),
                store,
                Runnable::run,
                new SkillRagExtractionJobSettings(20, 10, 4));

        var submitted = service.submitAllChunks("attachment", "42", null);
        var job = service.getJob(submitted.jobId());

        assertEquals(SkillRagExtractionJobStatus.FAILED, job.status());
        assertEquals("RAG chunk text exceeds maxTextBytesPerBatch",
                service.listItems(job.jobId(), 0, 10).get(0).error());
    }

    @Test
    void listsJobsWithStatusAndObjectFilters() {
        InMemorySkillRagExtractionJobStore store = new InMemorySkillRagExtractionJobStore();
        DefaultSkillRagExtractionJobService service = new DefaultSkillRagExtractionJobService(
                new CountingExtractionService(),
                new PagingResolver(List.of(chunk("doc-1", "chunk-1", "Spring Boot"))),
                store,
                Runnable::run,
                new SkillRagExtractionJobSettings(20, 10, 1_000_000));

        service.submitAllChunks("attachment", "42", null);
        service.submitAllChunks("attachment", "43", null);

        var jobs = service.listJobs("COMPLETED", "attachment", "42", 0, 10);

        assertEquals(1, jobs.size());
        assertEquals("42", jobs.get(0).objectId());
        assertNull(jobs.get(0).documentId());
    }

    @Test
    void getJobReconcilesCompletedRunningJob() {
        InMemorySkillRagExtractionJobStore store = new InMemorySkillRagExtractionJobStore();
        DefaultSkillRagExtractionJobService service = new DefaultSkillRagExtractionJobService(
                new CountingExtractionService(),
                new PagingResolver(List.of()),
                store,
                Runnable::run,
                new SkillRagExtractionJobSettings(20, 10, 1_000_000));
        Instant now = Instant.now();
        store.saveJob(new SkillRagExtractionJob(
                "job-1",
                "attachment",
                "42",
                "doc-1",
                SkillRagExtractionJobStatus.RUNNING,
                10,
                2,
                2,
                2,
                0,
                2,
                null,
                now,
                now));

        var job = service.getJob("job-1");

        assertEquals(SkillRagExtractionJobStatus.COMPLETED, job.status());
        assertEquals(SkillRagExtractionJobStatus.COMPLETED, store.findJob("job-1").orElseThrow().status());
    }

    @Test
    void listJobsReconcilesPartialRunningJobBeforeFiltering() {
        InMemorySkillRagExtractionJobStore store = new InMemorySkillRagExtractionJobStore();
        DefaultSkillRagExtractionJobService service = new DefaultSkillRagExtractionJobService(
                new CountingExtractionService(),
                new PagingResolver(List.of()),
                store,
                Runnable::run,
                new SkillRagExtractionJobSettings(20, 10, 1_000_000));
        Instant now = Instant.now();
        store.saveJob(new SkillRagExtractionJob(
                "job-1",
                "attachment",
                "42",
                "doc-1",
                SkillRagExtractionJobStatus.RUNNING,
                10,
                2,
                2,
                1,
                1,
                1,
                null,
                now,
                now));

        var jobs = service.listJobs("PARTIAL", "attachment", "42", 0, 10);

        assertEquals(1, jobs.size());
        assertEquals(SkillRagExtractionJobStatus.PARTIAL, jobs.get(0).status());
        assertEquals(SkillRagExtractionJobStatus.PARTIAL, store.findJob("job-1").orElseThrow().status());
    }

    private static ResolvedRagChunk chunk(String documentId, String chunkId, String content) {
        return new ResolvedRagChunk(chunkId, documentId, content);
    }

    private static ResolvedRagChunk chunk(String documentId, String objectId, String chunkId, String content) {
        return new ResolvedRagChunk(chunkId, documentId, objectId, content, null, null, null, null, null);
    }

    private static ObjectTypeRuntimeService objectTypeService(int objectType, String code) {
        return new ObjectTypeRuntimeService() {
            @Override
            public ObjectTypeDefinition definition(int requestedObjectType) {
                if (objectType != requestedObjectType) {
                    throw new IllegalArgumentException(String.valueOf(requestedObjectType));
                }
                return new ObjectTypeDefinition(new ObjectTypeView(
                        objectType,
                        code,
                        code,
                        null,
                        "ACTIVE",
                        null,
                        null,
                        0L,
                        null,
                        null,
                        0L,
                        null), null);
            }

            @Override
            public ValidateUploadResult validateUpload(int objectType, ValidateUploadCommand request) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class PagingResolver implements SkillGraphRagChunkResolver {

        private final List<ResolvedRagChunk> chunks;
        private final List<Integer> offsets = new ArrayList<>();
        private final List<String> objectTypes = new ArrayList<>();
        private final List<String> objectIds = new ArrayList<>();

        private PagingResolver(List<ResolvedRagChunk> chunks) {
            this.chunks = chunks;
        }

        @Override
        public List<ResolvedRagChunk> listByObject(String objectType, String objectId, int limit) {
            return listByObject(objectType, objectId, 0, limit);
        }

        @Override
        public long countByObject(String objectType, String objectId) {
            return chunks.size();
        }

        @Override
        public List<ResolvedRagChunk> listByObject(String objectType, String objectId, int offset, int limit) {
            objectTypes.add(objectType);
            objectIds.add(objectId);
            offsets.add(offset);
            int from = Math.min(offset, chunks.size());
            int to = Math.min(from + limit, chunks.size());
            return chunks.subList(from, to);
        }

        @Override
        public List<ResolvedRagChunk> listByObject(
                String objectType,
                String objectId,
                String query,
                int offset,
                int limit) {
            objectTypes.add(objectType);
            objectIds.add(objectId);
            offsets.add(offset);
            List<ResolvedRagChunk> filtered = chunks.stream()
                    .filter(chunk -> query == null
                            || chunk.content().toLowerCase().contains(query.toLowerCase()))
                    .toList();
            int from = Math.min(offset, filtered.size());
            int to = Math.min(from + limit, filtered.size());
            return filtered.subList(from, to);
        }

        @Override
        public long countByObject(String objectType, String objectId, String query) {
            return chunks.stream()
                    .filter(chunk -> query == null
                            || chunk.content().toLowerCase().contains(query.toLowerCase()))
                    .count();
        }
    }

    private static final class CountingExtractionService implements SkillExtractionService {

        @Override
        public SkillExtractionResult extract(SkillExtractionCommand command) {
            return new SkillExtractionResult("source-" + command.chunkId(), 1, List.of());
        }
    }
}
