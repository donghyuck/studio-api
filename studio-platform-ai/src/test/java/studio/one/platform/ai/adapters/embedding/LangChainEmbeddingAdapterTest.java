package studio.one.platform.ai.adapters.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LangChainEmbeddingAdapterTest {

    @Test
    void shouldConvertLangChainEmbedding() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        List<TextSegment> segments = List.of(TextSegment.from("hello"), TextSegment.from("world"));
        when(model.embedAll(segments))
                .thenReturn(Response.from(List.of(Embedding.from(new float[]{1.0f, 2.0f}), Embedding.from(new float[]{3.0f, 4.0f}))));

        LangChainEmbeddingAdapter adapter = new LangChainEmbeddingAdapter(model);
        EmbeddingResponse response = adapter.embed(new EmbeddingRequest(List.of("hello", "world")));

        assertThat(response.vectors()).hasSize(2);
        assertThat(response.vectors().get(0).values()).containsExactly(1.0d, 2.0d);
        assertThat(response.vectors().get(1).values()).containsExactly(3.0d, 4.0d);
        verify(model).embedAll(segments);
    }
}
