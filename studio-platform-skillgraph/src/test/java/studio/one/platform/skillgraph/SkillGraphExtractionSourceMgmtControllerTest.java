package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import studio.one.platform.skillgraph.application.result.ResolvedRagChunk;
import studio.one.platform.skillgraph.application.usecase.SkillGraphRagChunkResolver;
import studio.one.platform.skillgraph.web.controller.SkillGraphExtractionSourceMgmtController;

class SkillGraphExtractionSourceMgmtControllerTest {

    @Test
    void pagesRagChunksForSkillGraphPreview() {
        String longContent = "Spring Boot ".repeat(40);
        SkillGraphExtractionSourceMgmtController controller = controller(List.of(
                chunk("doc-1", "chunk-1", longContent, 0),
                chunk("doc-1", "chunk-2", "JPA content", 1),
                chunk("doc-1", "chunk-3", "Security content", 2)));

        var page = controller.ragChunks("attachment", "42", null, null, 1, 1, null)
                .getBody()
                .getData();

        assertEquals(1, page.offset());
        assertEquals(1, page.limit());
        assertEquals(1, page.returned());
        assertTrue(page.hasMore());
        assertEquals(null, page.total());
        assertEquals("chunk-2", page.items().get(0).chunkId());
        assertEquals("JPA content", page.items().get(0).textPreview());

        var firstPage = controller.ragChunks("attachment", "42", null, null, 0, 1, null)
                .getBody()
                .getData();

        assertTrue(firstPage.items().get(0).textPreview().length() < longContent.length());
        assertEquals(longContent.length(), firstPage.items().get(0).textLength());
    }

    @Test
    void filtersRagChunkPreviewByQueryAndDocument() {
        SkillGraphExtractionSourceMgmtController controller = controller(List.of(
                chunk("doc-1", "chunk-1", "Spring Boot content", 0),
                chunk("doc-2", "chunk-2", "JPA content", 1),
                chunk("doc-2", "chunk-3", "Security content", 2)));

        var page = controller.ragChunks("attachment", "42", "doc-2", "security", 0, 10, null)
                .getBody()
                .getData();

        assertEquals(1, page.total());
        assertFalse(page.hasMore());
        assertEquals("chunk-3", page.items().get(0).chunkId());
        assertEquals("WARNING", page.items().get(0).warningStatus());
    }

    @Test
    void rejectsRagChunkPreviewWhenResolverIsUnavailable() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        SkillGraphExtractionSourceMgmtController controller = new SkillGraphExtractionSourceMgmtController(
                beanFactory.getBeanProvider(SkillGraphRagChunkResolver.class));

        assertThrows(RuntimeException.class, () -> controller.ragChunks(
                "attachment", "42", null, null, 0, 10, null));
    }

    private SkillGraphExtractionSourceMgmtController controller(List<ResolvedRagChunk> chunks) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("skillGraphRagChunkResolver", new FakeRagChunkResolver(chunks));
        return new SkillGraphExtractionSourceMgmtController(
                beanFactory.getBeanProvider(SkillGraphRagChunkResolver.class));
    }

    private static ResolvedRagChunk chunk(String documentId, String chunkId, String content, int order) {
        String warningStatus = "chunk-3".equals(chunkId) ? "WARNING" : null;
        return new ResolvedRagChunk(chunkId, documentId, content, order, order + 1, "Section", 10, warningStatus);
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

        @Override
        public List<ResolvedRagChunk> listByObject(String objectType, String objectId, int offset, int limit) {
            int start = Math.max(0, offset);
            int end = Math.min(chunks.size(), start + Math.max(0, limit));
            return start >= end ? List.of() : chunks.subList(start, end);
        }
    }
}
