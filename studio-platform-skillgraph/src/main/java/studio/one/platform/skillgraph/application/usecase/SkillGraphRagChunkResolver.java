package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import studio.one.platform.skillgraph.application.result.ResolvedRagChunk;

public interface SkillGraphRagChunkResolver {

    List<ResolvedRagChunk> listByObject(String objectType, String objectId, int limit);
}
