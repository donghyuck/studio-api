package studio.one.platform.ai.autoconfigure.adapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.HttpOptions;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingPurpose;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;

/**
 * Google GenAI embedding adapter that preserves the index/query task contract.
 *
 * <p>Spring AI 2.0.0 stores {@code taskType} in
 * {@code GoogleGenAiTextEmbeddingOptions}, but does not copy it into the
 * provider SDK's {@link EmbedContentConfig}. This adapter keeps the Spring AI
 * connection contract while issuing the provider request with the complete
 * embedding-space options.</p>
 */
public final class GoogleGenAiEmbeddingAdapter implements EmbeddingPort {

    @FunctionalInterface
    interface Client {
        EmbedContentResponse embed(String model, List<String> texts, EmbedContentConfig config);
    }

    private static final String PROVIDER_DEFAULT = "provider-default";
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final Client client;
    private final String modelEndpoint;
    private final String configuredModel;
    private final Integer configuredDimension;
    private final String indexTaskType;
    private final String queryTaskType;
    private final String defaultTaskType;
    private final boolean taskTypeSupported;
    private final int requestTimeoutMillis;

    public GoogleGenAiEmbeddingAdapter(
            org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails connectionDetails,
            String configuredModel,
            Integer configuredDimension,
            String indexTaskType,
            String queryTaskType,
            String defaultTaskType,
            boolean taskTypeSupported) {
        this(
                connectionDetails,
                configuredModel,
                configuredDimension,
                indexTaskType,
                queryTaskType,
                defaultTaskType,
                taskTypeSupported,
                DEFAULT_REQUEST_TIMEOUT);
    }

    public GoogleGenAiEmbeddingAdapter(
            org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails connectionDetails,
            String configuredModel,
            Integer configuredDimension,
            String indexTaskType,
            String queryTaskType,
            String defaultTaskType,
            boolean taskTypeSupported,
            Duration requestTimeout) {
        this(
                (model, texts, config) ->
                        connectionDetails.getGenAiClient().models.embedContent(model, texts, config),
                connectionDetails.getModelEndpointName(configuredModel),
                configuredModel,
                configuredDimension,
                indexTaskType,
                queryTaskType,
                defaultTaskType,
                taskTypeSupported,
                requestTimeout);
    }

    GoogleGenAiEmbeddingAdapter(
            Client client,
            String modelEndpoint,
            String configuredModel,
            Integer configuredDimension,
            String indexTaskType,
            String queryTaskType,
            String defaultTaskType,
            boolean taskTypeSupported) {
        this(
                client,
                modelEndpoint,
                configuredModel,
                configuredDimension,
                indexTaskType,
                queryTaskType,
                defaultTaskType,
                taskTypeSupported,
                DEFAULT_REQUEST_TIMEOUT);
    }

    GoogleGenAiEmbeddingAdapter(
            Client client,
            String modelEndpoint,
            String configuredModel,
            Integer configuredDimension,
            String indexTaskType,
            String queryTaskType,
            String defaultTaskType,
            boolean taskTypeSupported,
            Duration requestTimeout) {
        this.client = Objects.requireNonNull(client, "client");
        this.modelEndpoint = requireText(modelEndpoint, "modelEndpoint");
        this.configuredModel = requireText(configuredModel, "configuredModel");
        this.configuredDimension = configuredDimension;
        this.indexTaskType = normalizeTaskType(indexTaskType);
        this.queryTaskType = normalizeTaskType(queryTaskType);
        this.defaultTaskType = normalizeTaskType(defaultTaskType);
        this.taskTypeSupported = taskTypeSupported;
        this.requestTimeoutMillis = timeoutMillis(requestTimeout);
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        if (request.model() != null && !configuredModel.equals(request.model())) {
            throw new IllegalArgumentException(
                    "Embedding model '" + request.model()
                            + "' does not match configured Google GenAI embedding model '"
                            + configuredModel + "'");
        }

        EmbedContentConfig.Builder config = EmbedContentConfig.builder();
        if (configuredDimension != null) {
            config.outputDimensionality(configuredDimension);
        }
        String taskType = taskType(request.purpose());
        if (taskType != null) {
            config.taskType(taskType);
        }
        config.httpOptions(HttpOptions.builder().timeout(requestTimeoutMillis));

        EmbedContentResponse providerResponse =
                client.embed(modelEndpoint, request.texts(), config.build());
        List<ContentEmbedding> embeddings = providerResponse.embeddings()
                .orElseThrow(() -> new IllegalStateException(
                        "Google GenAI embedding response did not contain embeddings"));
        if (embeddings.size() != request.texts().size()) {
            throw new IllegalStateException(
                    "Google GenAI embedding response size mismatch: requested=%d, actual=%d"
                            .formatted(request.texts().size(), embeddings.size()));
        }

        List<EmbeddingVector> vectors = new ArrayList<>(embeddings.size());
        for (int index = 0; index < embeddings.size(); index++) {
            List<Float> raw = embeddings.get(index).values()
                    .orElseThrow(() -> new IllegalStateException(
                            "Google GenAI embedding response contained an empty vector"));
            if (configuredDimension != null && raw.size() != configuredDimension) {
                throw new IllegalStateException(
                        "Google GenAI embedding dimension mismatch: configured=%d, actual=%d"
                                .formatted(configuredDimension, raw.size()));
            }
            vectors.add(new EmbeddingVector(
                    request.texts().get(index),
                    raw.stream().map(Float::doubleValue).toList()));
        }
        return new EmbeddingResponse(vectors);
    }

    private String taskType(EmbeddingPurpose purpose) {
        if (!taskTypeSupported) {
            return null;
        }
        String selected = switch (purpose) {
            case INDEX -> firstExplicit(indexTaskType, defaultTaskType);
            case QUERY -> firstExplicit(queryTaskType, defaultTaskType);
            case UNSPECIFIED -> defaultTaskType;
        };
        return isProviderDefault(selected) ? null : selected;
    }

    private static String firstExplicit(String preferred, String fallback) {
        return isProviderDefault(preferred) ? fallback : preferred;
    }

    private static boolean isProviderDefault(String value) {
        return value == null || PROVIDER_DEFAULT.equalsIgnoreCase(value);
    }

    private static String normalizeTaskType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return PROVIDER_DEFAULT.equalsIgnoreCase(value.trim()) ? PROVIDER_DEFAULT : normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static int timeoutMillis(Duration requestTimeout) {
        Duration timeout = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        long millis = timeout.toMillis();
        if (millis == 0L) {
            throw new IllegalArgumentException("requestTimeout must be at least 1ms");
        }
        if (millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("requestTimeout exceeds Google GenAI SDK limit");
        }
        return Math.toIntExact(millis);
    }
}
