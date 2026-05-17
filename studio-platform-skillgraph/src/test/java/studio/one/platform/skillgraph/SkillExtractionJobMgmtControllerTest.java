package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import studio.one.platform.skillgraph.application.command.SkillExtractionCommand;
import studio.one.platform.skillgraph.application.result.ResolvedRagChunk;
import studio.one.platform.skillgraph.application.result.SkillExtractionResult;
import studio.one.platform.skillgraph.application.service.DefaultSkillExtractionService;
import studio.one.platform.skillgraph.application.usecase.SkillExtractionService;
import studio.one.platform.skillgraph.application.usecase.SkillGraphRagChunkResolver;
import studio.one.platform.skillgraph.infrastructure.extraction.PatternSkillCandidateExtractor;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillCandidateStore;
import studio.one.platform.skillgraph.web.controller.SkillExtractionJobMgmtController;
import studio.one.platform.skillgraph.web.dto.request.SkillRagExtractionRequest;
import studio.one.platform.skillgraph.web.dto.request.SkillRagChunkExtractionRequest;
import studio.one.platform.skillgraph.web.dto.request.SkillRagDocumentExtractionRequest;

class SkillExtractionJobMgmtControllerTest {

    @Test
    void extractsRagDocumentChunksWithoutClientText() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        SkillExtractionJobMgmtController controller = controller(store, new FakeRagChunkResolver(List.of(
                ragChunk("doc-1", "chunk-1", "Spring Boot 기술"),
                ragChunk("doc-2", "chunk-2", "Kubernetes 기술"))));

        var response = controller.extractRagDocument(new SkillRagDocumentExtractionRequest(
                "attachment", "42", "doc-1", "ALL_CHUNKS", null)).getBody().getData();

        assertEquals(1, response.resolvedChunks());
        assertEquals(1, response.succeededChunks());
        assertEquals(0, response.failedChunks());
        assertEquals("RAG_CHUNK", store.sourceChunks().get(0).sourceType());
        assertEquals("doc-1", store.sourceChunks().get(0).sourceId());
        assertEquals("chunk-1", store.sourceChunks().get(0).chunkId());
    }

    @Test
    void extractsSelectedRagChunksAndReportsMissingChunks() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        SkillExtractionJobMgmtController controller = controller(store, new FakeRagChunkResolver(List.of(
                ragChunk("doc-1", "chunk-1", "Spring Boot 기술"))));

        var response = controller.extractRagChunks(new SkillRagChunkExtractionRequest(
                "attachment", "42", null, List.of("chunk-1", "missing"))).getBody().getData();

        assertEquals(2, response.requestedChunks());
        assertEquals(1, response.resolvedChunks());
        assertEquals(1, response.succeededChunks());
        assertEquals(1, response.failedChunks());
        assertEquals("NOT_FOUND", response.items().get(1).status());
    }

    @Test
    void extractsSelectedRagChunksThroughUnifiedRagEndpoint() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        SkillExtractionJobMgmtController controller = controller(store, new FakeRagChunkResolver(List.of(
                ragChunk("doc-1", "chunk-1", "Spring Boot 기술"))));

        var response = controller.extractRag(new SkillRagExtractionRequest(
                "attachment",
                "42",
                "doc-1",
                "SELECTED_CHUNKS",
                List.of("chunk-1"),
                null)).getBody().getData();

        assertEquals(1, response.succeededChunks());
        assertEquals("chunk-1", store.sourceChunks().get(0).chunkId());
    }

    @Test
    void rejectsUnifiedRagEndpointWithoutSelectedChunkIds() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        SkillExtractionJobMgmtController controller = controller(store, new FakeRagChunkResolver(List.of(
                ragChunk("doc-1", "chunk-1", "Spring Boot 기술"))));

        assertThrows(RuntimeException.class, () -> controller.extractRag(new SkillRagExtractionRequest(
                "attachment",
                "42",
                "doc-1",
                "SELECTED_CHUNKS",
                null,
                null)));
    }

    @Test
    void rejectsRagExtractionWhenPipelineIsUnavailable() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        SkillExtractionJobMgmtController controller = controller(store, null);

        assertThrows(RuntimeException.class, () -> controller.extractRagDocument(
                new SkillRagDocumentExtractionRequest("attachment", "42", null, "ALL_CHUNKS", null)));
    }

    @Test
    void hidesUnexpectedExtractionFailureDetails() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        SkillExtractionJobMgmtController controller = new SkillExtractionJobMgmtController(
                new FailingSkillExtractionService(),
                resolverProvider(new FakeRagChunkResolver(List.of(ragChunk("doc-1", "chunk-1", "Spring Boot")))));

        var response = controller.extractRagChunks(new SkillRagChunkExtractionRequest(
                "attachment", "42", null, List.of("chunk-1"))).getBody().getData();

        assertEquals(1, response.failedChunks());
        assertEquals("Skill extraction failed", response.items().get(0).error());
    }

    private SkillExtractionJobMgmtController controller(
            InMemorySkillCandidateStore store,
            SkillGraphRagChunkResolver resolver) {
        DefaultSkillExtractionService extractionService = new DefaultSkillExtractionService(store,
                new PatternSkillCandidateExtractor());
        return new SkillExtractionJobMgmtController(
                extractionService,
                resolverProvider(resolver));
    }

    private org.springframework.beans.factory.ObjectProvider<SkillGraphRagChunkResolver> resolverProvider(
            SkillGraphRagChunkResolver resolver) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        if (resolver != null) {
            beanFactory.addBean("skillGraphRagChunkResolver", resolver);
        }
        return beanFactory.getBeanProvider(SkillGraphRagChunkResolver.class);
    }

    private static ResolvedRagChunk ragChunk(String documentId, String chunkId, String content) {
        return new ResolvedRagChunk(chunkId, documentId, content);
    }

    private static final class FakeRagChunkResolver implements SkillGraphRagChunkResolver {

        private final List<ResolvedRagChunk> chunks;

        private FakeRagChunkResolver(List<ResolvedRagChunk> chunks) {
            this.chunks = chunks;
        }

        @Override
        public List<ResolvedRagChunk> listByObject(String objectType, String objectId, int limit) {
            int max = limit <= 0 ? chunks.size() : Math.min(limit, chunks.size());
            return chunks.subList(0, max);
        }
    }

    private static final class FailingSkillExtractionService implements SkillExtractionService {

        @Override
        public SkillExtractionResult extract(SkillExtractionCommand command) {
            throw new IllegalStateException("database password leaked");
        }

        @Override
        public SkillExtractionResult dryRun(SkillExtractionCommand command) {
            throw new IllegalStateException("database password leaked");
        }
    }
}
