package studio.one.platform.skillgraph.domain.port;

import java.util.Optional;

import studio.one.platform.skillgraph.domain.model.SkillSourceChunk;

public interface SkillSourceChunkResolver {

    Optional<SkillSourceChunk> resolve(String sourceChunkId);
}
