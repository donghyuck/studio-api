package studio.one.platform.ai.service.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import studio.one.platform.ai.core.chunk.TextChunker;
import studio.one.platform.ai.core.rag.RagIndexRequest;

@SuppressWarnings("deprecation")
final class LegacyTextChunkerAdapter implements RagChunker {

    private final TextChunker textChunker;

    LegacyTextChunkerAdapter(TextChunker textChunker) {
        this.textChunker = Objects.requireNonNull(textChunker, "textChunker");
    }

    @Override
    public List<RagPipelineChunk> chunk(String indexedText, RagIndexRequest request) {
        return textChunker.chunk(request.documentId(), indexedText).stream()
                .map(chunk -> new RagPipelineChunk(chunk.id(), chunk.content(), Map.of()))
                .toList();
    }
}
