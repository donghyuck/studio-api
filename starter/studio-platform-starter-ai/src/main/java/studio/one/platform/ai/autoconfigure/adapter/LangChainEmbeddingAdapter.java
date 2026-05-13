package studio.one.platform.ai.autoconfigure.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;

/**
 * LangChain4j based {@link EmbeddingPort} adapter.
 */
public class LangChainEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel embeddingModel;
    private final String configuredModel;

    public LangChainEmbeddingAdapter(EmbeddingModel embeddingModel, String configuredModel) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        this.configuredModel = normalize(configuredModel);
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        if (request.model() != null && configuredModel != null && !configuredModel.equals(request.model())) {
            throw new IllegalArgumentException(
                    "Embedding model '" + request.model()
                            + "' does not match configured LangChain4j embedding model '" + configuredModel + "'");
        }
        List<TextSegment> segments = new ArrayList<>();
        for (String text : request.texts()) {
            segments.add(TextSegment.from(text));
        }
        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        List<Embedding> embeddings = response.content();
        List<EmbeddingVector> vectors = new ArrayList<>(embeddings.size());
        for (int index = 0; index < embeddings.size(); index++) {
            float[] rawValues = embeddings.get(index).vector();
            List<Double> values = new ArrayList<>(rawValues.length);
            for (float rawValue : rawValues) {
                values.add((double) rawValue);
            }
            vectors.add(new EmbeddingVector(request.texts().get(index), values));
        }
        return new EmbeddingResponse(vectors);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
