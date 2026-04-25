package studio.one.platform.ai.service.pipeline;

import java.util.Map;

record RagPipelineChunk(String id, String content, Map<String, Object> metadata) {

    RagPipelineChunk {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
