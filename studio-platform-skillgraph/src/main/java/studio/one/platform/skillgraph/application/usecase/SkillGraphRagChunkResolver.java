package studio.one.platform.skillgraph.application.usecase;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.result.ResolvedRagChunk;

public interface SkillGraphRagChunkResolver {

    List<ResolvedRagChunk> listByObject(String objectType, String objectId, int limit);

    default long countByObject(String objectType, String objectId) {
        return listByObject(objectType, objectId, Integer.MAX_VALUE).size();
    }

    default Page<ResolvedRagChunk> pageByObject(String objectType, String objectId, Pageable pageable) {
        int offset = pageable == null ? 0 : (int) Math.min(Integer.MAX_VALUE, pageable.getOffset());
        int limit = pageable == null || pageable.getPageSize() <= 0 ? 50 : pageable.getPageSize();
        return new PageImpl<>(
                listByObject(objectType, objectId, offset, limit),
                pageable,
                countByObject(objectType, objectId));
    }

    default Page<ResolvedRagChunk> pageByObjectType(String objectType, Pageable pageable) {
        return pageByObject(objectType, null, pageable);
    }

    default List<ResolvedRagChunk> listByObject(String objectType, String objectId, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit <= 0 ? 50 : limit;
        long requested = (long) safeOffset + safeLimit;
        int fetchLimit = requested > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) requested;
        return listByObject(objectType, objectId, fetchLimit).stream()
                .skip(safeOffset)
                .limit(safeLimit)
                .toList();
    }

    default List<ResolvedRagChunk> listByObject(
            String objectType,
            String objectId,
            String documentId,
            String query,
            int offset,
            int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit <= 0 ? 50 : limit;
        return listByObject(objectType, objectId, safeOffset, safeLimit).stream()
                .filter(chunk -> documentId == null || documentId.equals(chunk.documentId()))
                .filter(chunk -> matchesQuery(chunk, query))
                .toList();
    }

    default long countByObject(
            String objectType,
            String objectId,
            String documentId,
            String query) {
        return listByObject(objectType, objectId, documentId, query, 0, Integer.MAX_VALUE).size();
    }

    default Page<ResolvedRagChunk> pageByObject(
            String objectType,
            String objectId,
            String documentId,
            String query,
            Pageable pageable) {
        int offset = pageable == null ? 0 : (int) Math.min(Integer.MAX_VALUE, pageable.getOffset());
        int limit = pageable == null || pageable.getPageSize() <= 0 ? 50 : pageable.getPageSize();
        return new PageImpl<>(
                listByObject(objectType, objectId, documentId, query, offset, limit),
                pageable,
                countByObject(objectType, objectId, documentId, query));
    }

    private static boolean matchesQuery(ResolvedRagChunk chunk, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        String haystack = (String.valueOf(chunk.chunkId()) + " "
                + String.valueOf(chunk.documentId()) + " "
                + String.valueOf(chunk.section()) + " "
                + String.valueOf(chunk.content())).toLowerCase(Locale.ROOT);
        return haystack.contains(normalizedQuery);
    }
}
