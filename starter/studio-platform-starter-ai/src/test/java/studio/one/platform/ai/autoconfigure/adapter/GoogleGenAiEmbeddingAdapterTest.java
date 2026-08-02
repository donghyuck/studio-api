package studio.one.platform.ai.autoconfigure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import org.junit.jupiter.api.Test;
import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.core.embedding.EmbeddingPurpose;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;

class GoogleGenAiEmbeddingAdapterTest {

    @Test
    void sendsDifferentProviderTaskTypesForIndexAndQueryRequests() {
        AtomicReference<EmbedContentConfig> captured = new AtomicReference<>();
        GoogleGenAiEmbeddingAdapter adapter = adapter(captured, true);

        adapter.embed(request(EmbeddingPurpose.INDEX));
        assertThat(captured.get().taskType()).contains("RETRIEVAL_DOCUMENT");
        assertThat(captured.get().outputDimensionality()).contains(2);

        adapter.embed(request(EmbeddingPurpose.QUERY));
        assertThat(captured.get().taskType()).contains("RETRIEVAL_QUERY");
    }

    @Test
    void omitsTaskTypeForModelsThatDoNotSupportIt() {
        AtomicReference<EmbedContentConfig> captured = new AtomicReference<>();
        GoogleGenAiEmbeddingAdapter adapter = adapter(captured, false);

        adapter.embed(request(EmbeddingPurpose.QUERY));

        assertThat(captured.get().taskType()).isEmpty();
        assertThat(captured.get().outputDimensionality()).contains(2);
    }

    @Test
    void appliesBoundedRequestTimeoutToEveryProviderCall() {
        AtomicReference<EmbedContentConfig> captured = new AtomicReference<>();
        GoogleGenAiEmbeddingAdapter adapter = new GoogleGenAiEmbeddingAdapter(
                (model, texts, config) -> {
                    captured.set(config);
                    return response(List.of(1.0f, 2.0f));
                },
                "models/gemini-embedding-001",
                "gemini-embedding-001",
                2,
                "retrieval_document",
                "retrieval_query",
                null,
                true,
                Duration.ofMillis(1_500));

        adapter.embed(request(EmbeddingPurpose.INDEX));

        assertThat(captured.get().httpOptions())
                .isPresent()
                .get()
                .extracting(options -> options.timeout().orElseThrow())
                .isEqualTo(1_500);
    }

    @Test
    void rejectsNonPositiveRequestTimeout() {
        assertThatThrownBy(() -> new GoogleGenAiEmbeddingAdapter(
                        (model, texts, config) -> response(List.of(1.0f, 2.0f)),
                        "models/gemini-embedding-001",
                        "gemini-embedding-001",
                        2,
                        "retrieval_document",
                        "retrieval_query",
                        null,
                        true,
                        Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsProviderResponseWithUnexpectedDimension() {
        GoogleGenAiEmbeddingAdapter adapter = new GoogleGenAiEmbeddingAdapter(
                (model, texts, config) -> response(List.of(1.0f)),
                "models/gemini-embedding-001",
                "gemini-embedding-001",
                2,
                "retrieval_document",
                "retrieval_query",
                null,
                true);

        assertThatThrownBy(() -> adapter.embed(request(EmbeddingPurpose.INDEX)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dimension mismatch");
    }

    private GoogleGenAiEmbeddingAdapter adapter(
            AtomicReference<EmbedContentConfig> captured,
            boolean taskTypeSupported) {
        return new GoogleGenAiEmbeddingAdapter(
                (model, texts, config) -> {
                    captured.set(config);
                    return response(List.of(1.0f, 2.0f));
                },
                "models/gemini-embedding-001",
                "gemini-embedding-001",
                2,
                "retrieval_document",
                "retrieval_query",
                null,
                taskTypeSupported);
    }

    private EmbeddingRequest request(EmbeddingPurpose purpose) {
        return new EmbeddingRequest(
                List.of("text"),
                "google",
                "gemini-embedding-001",
                EmbeddingInputType.TEXT,
                purpose,
                Map.of());
    }

    private static EmbedContentResponse response(List<Float> values) {
        return EmbedContentResponse.builder()
                .embeddings(ContentEmbedding.builder().values(values))
                .build();
    }
}
