package studio.one.platform.ai.autoconfigure.adapter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;

/**
 * Embedding adapter for Hugging Face Text Embeddings Inference servers.
 */
public class TeiEmbeddingAdapter implements EmbeddingPort {

    private static final TypeReference<List<List<Double>>> EMBEDDING_LIST_TYPE = new TypeReference<>() {
    };

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI embedUri;
    private final String configuredModel;

    public TeiEmbeddingAdapter(String baseUrl, String configuredModel) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(),
                new ObjectMapper(),
                baseUrl,
                configuredModel);
    }

    TeiEmbeddingAdapter(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, String configuredModel) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.embedUri = URI.create(normalizeBaseUrl(baseUrl) + "/embed");
        this.configuredModel = normalize(configuredModel);
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        if (request.model() != null && configuredModel != null
                && !configuredModel.equals(request.model())) {
            throw new IllegalArgumentException(
                    "Embedding model '" + request.model()
                            + "' does not match configured TEI embedding model '" + configuredModel + "'");
        }

        List<List<Double>> rawVectors = exchange(request.texts());
        if (rawVectors.size() != request.texts().size()) {
            throw new IllegalStateException("TEI embedding response vector count " + rawVectors.size()
                    + " does not match request text count " + request.texts().size());
        }

        List<EmbeddingVector> vectors = new ArrayList<>(rawVectors.size());
        for (int index = 0; index < rawVectors.size(); index++) {
            vectors.add(new EmbeddingVector(request.texts().get(index), rawVectors.get(index)));
        }
        return new EmbeddingResponse(vectors);
    }

    private List<List<Double>> exchange(List<String> texts) {
        try {
            String body = objectMapper.writeValueAsString(new TeiEmbeddingRequest(texts));
            HttpRequest httpRequest = HttpRequest.newBuilder(embedUri)
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("TEI embedding request failed with status "
                        + response.statusCode() + ": " + abbreviate(response.body()));
            }
            return objectMapper.readValue(response.body(), EMBEDDING_LIST_TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to call TEI embedding server", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling TEI embedding server", e);
        }
    }

    private static String normalizeBaseUrl(String value) {
        String baseUrl = normalize(value);
        if (baseUrl == null) {
            throw new IllegalArgumentException("TEI baseUrl must not be blank");
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "...";
    }

    static final class TeiEmbeddingRequest {

        private final List<String> inputs;

        @JsonCreator
        TeiEmbeddingRequest(@JsonProperty("inputs") List<String> inputs) {
            this.inputs = List.copyOf(inputs);
        }

        public List<String> getInputs() {
            return inputs;
        }
    }
}
