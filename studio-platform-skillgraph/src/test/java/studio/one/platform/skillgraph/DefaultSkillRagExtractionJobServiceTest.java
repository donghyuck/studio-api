package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.skillgraph.application.command.SkillExtractionCommand;
import studio.one.platform.skillgraph.application.result.ResolvedRagChunk;
import studio.one.platform.skillgraph.application.result.SkillExtractionResult;
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

        var submitted = service.submitAllChunks("attachment", "42", "doc-1", null);
        var job = service.getJob(submitted.jobId());

        assertEquals(SkillRagExtractionJobStatus.COMPLETED, job.status());
        assertEquals(3, job.totalChunks());
        assertEquals(3, job.processedChunks());
        assertEquals(3, job.succeededChunks());
        assertEquals(List.of(0, 2), resolver.offsets);
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

        var submitted = service.submitAllChunks("attachment", "42", "doc-1", null);
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

        var submitted = service.submitAllChunks("attachment", "42", "doc-1", null);
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

        service.submitAllChunks("attachment", "42", "doc-1", null);
        service.submitAllChunks("attachment", "43", "doc-2", null);

        var jobs = service.listJobs("COMPLETED", "attachment", "42", "doc-1", 0, 10);

        assertEquals(1, jobs.size());
        assertEquals("42", jobs.get(0).objectId());
        assertEquals("doc-1", jobs.get(0).documentId());
    }

    private static ResolvedRagChunk chunk(String documentId, String chunkId, String content) {
        return new ResolvedRagChunk(chunkId, documentId, content);
    }

    private static final class PagingResolver implements SkillGraphRagChunkResolver {

        private final List<ResolvedRagChunk> chunks;
        private final List<Integer> offsets = new ArrayList<>();

        private PagingResolver(List<ResolvedRagChunk> chunks) {
            this.chunks = chunks;
        }

        @Override
        public List<ResolvedRagChunk> listByObject(String objectType, String objectId, int limit) {
            return listByObject(objectType, objectId, 0, limit);
        }

        @Override
        public List<ResolvedRagChunk> listByObject(String objectType, String objectId, int offset, int limit) {
            offsets.add(offset);
            int from = Math.min(offset, chunks.size());
            int to = Math.min(from + limit, chunks.size());
            return chunks.subList(from, to);
        }
    }

    private static final class CountingExtractionService implements SkillExtractionService {

        @Override
        public SkillExtractionResult extract(SkillExtractionCommand command) {
            return new SkillExtractionResult("source-" + command.chunkId(), 1, List.of());
        }
    }
}
