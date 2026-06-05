package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import studio.one.platform.objecttype.application.command.ValidateUploadCommand;
import studio.one.platform.objecttype.application.result.ObjectTypeDefinition;
import studio.one.platform.objecttype.application.result.ObjectTypeView;
import studio.one.platform.objecttype.application.result.ValidateUploadResult;
import studio.one.platform.objecttype.application.usecase.ObjectTypeRuntimeService;
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

        var page = controller.ragChunks("attachment", "42", null, null, PageRequest.of(1, 1))
                .getBody()
                .getData();

        assertEquals(1, page.getNumber());
        assertEquals(1, page.getSize());
        assertEquals(3, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        assertTrue(page.hasNext());
        assertEquals("chunk-2", page.getContent().get(0).chunkId());
        assertEquals("JPA content", page.getContent().get(0).textPreview());

        var firstPage = controller.ragChunks("attachment", "42", null, null, PageRequest.of(0, 1))
                .getBody()
                .getData();

        assertTrue(firstPage.getContent().get(0).textPreview().length() < longContent.length());
        assertEquals(longContent.length(), firstPage.getContent().get(0).textLength());
    }

    @Test
    void filtersRagChunkPreviewByQueryAndDocument() {
        FakeRagChunkResolver resolver = new FakeRagChunkResolver(List.of(
                chunk("doc-1", "chunk-1", "Spring Boot content", 0),
                chunk("doc-2", "chunk-2", "JPA content", 1),
                chunk("doc-2", "chunk-3", "Security content", 2)));
        SkillGraphExtractionSourceMgmtController controller = controller(resolver);

        var page = controller.ragChunks("attachment", "42", "doc-2", "security", PageRequest.of(0, 10))
                .getBody()
                .getData();

        assertEquals(1, page.getTotalElements());
        assertFalse(page.hasNext());
        assertEquals("chunk-3", page.getContent().get(0).chunkId());
        assertEquals("WARNING", page.getContent().get(0).warningStatus());
        assertEquals("doc-2", resolver.documentId);
        assertEquals("security", resolver.query);
        assertEquals(0, resolver.offset);
        assertEquals(10, resolver.limit);
    }

    @Test
    void normalizesLegacyGenericAttachmentObjectTypeForRagChunks() {
        FakeRagChunkResolver resolver = new FakeRagChunkResolver(List.of(
                chunk("doc-1", "chunk-1", "Spring Boot content", 0)));
        SkillGraphExtractionSourceMgmtController controller = controller(resolver);

        var page = controller.ragChunks("2001", "42", null, null, PageRequest.of(0, 10))
                .getBody()
                .getData();

        assertEquals(1, page.getTotalElements());
        assertEquals("attachment", resolver.objectType);
        assertEquals("42", resolver.objectId);
    }

    @Test
    void normalizesLegacyAttachmentWithoutRuntimeLookup() {
        FakeRagChunkResolver resolver = new FakeRagChunkResolver(List.of(
                chunk("doc-1", "chunk-1", "Spring Boot content", 0)));
        ObjectTypeRuntimeService failingRuntimeService = new ObjectTypeRuntimeService() {
            @Override
            public ObjectTypeDefinition definition(int objectType) {
                throw new AssertionError("legacy attachment must not resolve object type policy");
            }

            @Override
            public ValidateUploadResult validateUpload(int objectType, ValidateUploadCommand request) {
                throw new UnsupportedOperationException();
            }
        };
        SkillGraphExtractionSourceMgmtController controller = controller(resolver, failingRuntimeService);

        var page = controller.ragChunks("2001", null, null, null, PageRequest.of(0, 50))
                .getBody()
                .getData();

        assertEquals(1, page.getTotalElements());
        assertEquals("attachment", resolver.objectType);
    }

    @Test
    void resolvesNumericObjectTypeToCodeBeforeRagChunkNormalization() {
        FakeRagChunkResolver resolver = new FakeRagChunkResolver(List.of(
                chunk("doc-1", "chunk-1", "Spring Boot content", 0)));
        SkillGraphExtractionSourceMgmtController controller = controller(resolver, objectTypeService(2001, "attachment"));

        var page = controller.ragChunks("2001", "42", null, null, PageRequest.of(0, 10))
                .getBody()
                .getData();

        assertEquals(1, page.getTotalElements());
        assertEquals("attachment", resolver.objectType);
        assertEquals("42", resolver.objectId);
    }

    @Test
    void rejectsRagChunkPreviewWhenResolverIsUnavailable() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        SkillGraphExtractionSourceMgmtController controller = new SkillGraphExtractionSourceMgmtController(
                beanFactory.getBeanProvider(SkillGraphRagChunkResolver.class));

        assertThrows(RuntimeException.class, () -> controller.ragChunks(
                "attachment", "42", null, null, PageRequest.of(0, 10)));
    }

    private SkillGraphExtractionSourceMgmtController controller(List<ResolvedRagChunk> chunks) {
        return controller(new FakeRagChunkResolver(chunks));
    }

    private SkillGraphExtractionSourceMgmtController controller(FakeRagChunkResolver resolver) {
        return controller(resolver, null);
    }

    private SkillGraphExtractionSourceMgmtController controller(
            FakeRagChunkResolver resolver,
            ObjectTypeRuntimeService objectTypeRuntimeService) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("skillGraphRagChunkResolver", resolver);
        if (objectTypeRuntimeService != null) {
            beanFactory.addBean("objectTypeRuntimeService", objectTypeRuntimeService);
        }
        return new SkillGraphExtractionSourceMgmtController(
                beanFactory.getBeanProvider(SkillGraphRagChunkResolver.class),
                beanFactory.getBeanProvider(ObjectTypeRuntimeService.class));
    }

    private static ResolvedRagChunk chunk(String documentId, String chunkId, String content, int order) {
        String warningStatus = "chunk-3".equals(chunkId) ? "WARNING" : null;
        return new ResolvedRagChunk(chunkId, documentId, content, order, order + 1, "Section", 10, warningStatus);
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
            public int objectTypeByKey(String requestedKey) {
                if (code.equals(requestedKey)) {
                    return objectType;
                }
                throw new IllegalArgumentException(requestedKey);
            }

            @Override
            public ValidateUploadResult validateUpload(int objectType, ValidateUploadCommand request) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class FakeRagChunkResolver implements SkillGraphRagChunkResolver {

        private final List<ResolvedRagChunk> chunks;
        private String objectType;
        private String objectId;
        private String documentId;
        private String query;
        private int offset;
        private int limit;

        private FakeRagChunkResolver(List<ResolvedRagChunk> chunks) {
            this.chunks = chunks;
        }

        @Override
        public List<ResolvedRagChunk> listByObject(String objectType, String objectId, int limit) {
            this.objectType = objectType;
            this.objectId = objectId;
            int max = limit <= 0 ? chunks.size() : Math.min(limit, chunks.size());
            return chunks.subList(0, max);
        }

        @Override
        public long countByObject(String objectType, String objectId) {
            return chunks.size();
        }

        @Override
        public List<ResolvedRagChunk> listByObject(String objectType, String objectId, int offset, int limit) {
            this.objectType = objectType;
            this.objectId = objectId;
            int start = Math.max(0, offset);
            int end = Math.min(chunks.size(), start + Math.max(0, limit));
            return start >= end ? List.of() : chunks.subList(start, end);
        }

        @Override
        public List<ResolvedRagChunk> listByObject(
                String objectType,
                String objectId,
                String documentId,
                String query,
                int offset,
                int limit) {
            this.objectType = objectType;
            this.objectId = objectId;
            this.documentId = documentId;
            this.query = query;
            this.offset = offset;
            this.limit = limit;
            int start = Math.max(0, offset);
            List<ResolvedRagChunk> filtered = chunks.stream()
                    .filter(chunk -> documentId == null || documentId.equals(chunk.documentId()))
                    .filter(chunk -> query == null || chunk.content().toLowerCase().contains(query.toLowerCase()))
                    .toList();
            int end = Math.min(filtered.size(), start + Math.max(0, limit));
            return start >= end ? List.of() : filtered.subList(start, end);
        }

        @Override
        public long countByObject(String objectType, String objectId, String documentId, String query) {
            return chunks.stream()
                    .filter(chunk -> documentId == null || documentId.equals(chunk.documentId()))
                    .filter(chunk -> query == null || chunk.content().toLowerCase().contains(query.toLowerCase()))
                    .count();
        }
    }
}
