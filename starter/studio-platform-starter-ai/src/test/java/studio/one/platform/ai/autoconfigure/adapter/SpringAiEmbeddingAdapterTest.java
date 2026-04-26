package studio.one.platform.ai.autoconfigure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

import studio.one.platform.ai.core.embedding.EmbeddingRequest;

class SpringAiEmbeddingAdapterTest {

    @Test
    void preservesInputOrderWhenMappingEmbeddingResponse() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embedForResponse(anyList())).thenReturn(new EmbeddingResponse(List.of(
                new Embedding(new float[] {1.0f, 2.0f}, 0),
                new Embedding(new float[] {3.0f, 4.0f}, 1))));

        SpringAiEmbeddingAdapter adapter = new SpringAiEmbeddingAdapter(model);

        studio.one.platform.ai.core.embedding.EmbeddingResponse response =
                adapter.embed(new EmbeddingRequest(List.of("first", "second")));

        assertThat(response.vectors()).hasSize(2);
        assertThat(response.vectors().get(0).referenceId()).isEqualTo("first");
        assertThat(response.vectors().get(0).values()).containsExactly(1.0, 2.0);
        assertThat(response.vectors().get(1).referenceId()).isEqualTo("second");
        assertThat(response.vectors().get(1).values()).containsExactly(3.0, 4.0);
    }

    @Test
    void rejectsRequestModelThatDoesNotMatchConfiguredModel() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        SpringAiEmbeddingAdapter adapter = new SpringAiEmbeddingAdapter(model, "text-embedding-004");

        assertThatThrownBy(() -> adapter.embed(new EmbeddingRequest(
                List.of("text"),
                null,
                "other-model",
                null,
                null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match configured Spring AI embedding model");
    }
}
