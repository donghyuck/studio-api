package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import studio.one.platform.skillgraph.application.result.ResolvedRagChunk;

public interface SkillGraphRagChunkResolver {

    List<ResolvedRagChunk> listByObject(String objectType, String objectId, int limit);

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
}
