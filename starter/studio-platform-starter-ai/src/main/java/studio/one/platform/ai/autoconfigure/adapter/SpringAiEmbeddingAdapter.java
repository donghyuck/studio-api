package studio.one.platform.ai.autoconfigure.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.ai.embedding.EmbeddingModel;

import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;

/**
 * Spring AI based {@link EmbeddingPort} adapter used for migration spike validation.
 */
public class SpringAiEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel embeddingModel;
    private final String configuredModel;

    public SpringAiEmbeddingAdapter(EmbeddingModel embeddingModel) {
        this(embeddingModel, null);
    }

    public SpringAiEmbeddingAdapter(EmbeddingModel embeddingModel, String configuredModel) {
        this.embeddingModel = embeddingModel;
        this.configuredModel = normalize(configuredModel);
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        if (request.model() != null && configuredModel != null
                && !configuredModel.equals(request.model())) {
            throw new IllegalArgumentException(
                    "Embedding model '" + request.model()
                            + "' does not match configured Spring AI embedding model '" + configuredModel + "'");
        }
        org.springframework.ai.embedding.EmbeddingResponse response =
                embeddingModel.embedForResponse(request.texts());

        List<EmbeddingVector> vectors = new ArrayList<>(response.getResults().size());
        for (int index = 0; index < response.getResults().size(); index++) {
            float[] raw = response.getResults().get(index).getOutput();
            List<Double> values = new ArrayList<>(raw.length);
            for (float value : raw) {
                values.add((double) value);
            }
            vectors.add(new EmbeddingVector(request.texts().get(index), values));
        }
        return new EmbeddingResponse(vectors);
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").isBlank() ? null : value.trim();
    }
}
