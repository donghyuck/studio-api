package studio.one.platform.ai.adapters.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter that bridges LangChain4j {@link EmbeddingModel} to the domain
 * {@link EmbeddingPort}.
 */
public class LangChainEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel embeddingModel;

    public LangChainEmbeddingAdapter(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {

        List<TextSegment> segments = request.texts()
                .stream()
                .map(TextSegment::from)
                .toList();
        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        List<Embedding> embeddings = response.content();
        List<EmbeddingVector> vectors = new ArrayList<>(embeddings.size());
        for (int index = 0; index < embeddings.size(); index++) {
            Embedding embedding = embeddings.get(index);
            float[] rawValues = embedding.vector();
            List<Double> values = new ArrayList<>(rawValues.length);
            for (float rawValue : rawValues) {
                values.add((double) rawValue);
            }
            vectors.add(new EmbeddingVector(request.texts().get(index), values));
        }
        return new EmbeddingResponse(vectors);
    }
}
