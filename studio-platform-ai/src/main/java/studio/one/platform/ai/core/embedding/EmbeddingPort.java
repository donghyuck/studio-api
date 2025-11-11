package studio.one.platform.ai.core.embedding;

import java.util.List;

/**
 * Contract for producing embedding vectors from raw text.
 */
public interface EmbeddingPort {

    EmbeddingResponse embed(EmbeddingRequest request);

    default EmbeddingResponse embedAll(List<String> texts) {
        return embed(new EmbeddingRequest(texts));
    }
}
