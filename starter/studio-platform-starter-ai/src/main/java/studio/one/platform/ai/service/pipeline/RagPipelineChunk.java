package studio.one.platform.ai.service.pipeline;

import java.util.Map;

final class RagPipelineChunk {
    private final String id;
    private final String content;
    private final Map<String, Object> metadata;

    RagPipelineChunk(String id, String content, Map<String, Object> metadata) {
        this.id = id;
        this.content = content;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    String id() {
        return id;
    }

    String content() {
        return content;
    }

    Map<String, Object> metadata() {
        return metadata;
    }
}
