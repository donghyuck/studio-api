package studio.one.platform.ai.core.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EmbeddingRequestContractTest {

    @Test
    void keepsLegacyTextOnlyConstructorDefaults() {
        EmbeddingRequest request = new EmbeddingRequest(List.of("hello"));

        assertThat(request.texts()).containsExactly("hello");
        assertThat(request.provider()).isNull();
        assertThat(request.model()).isNull();
        assertThat(request.inputType()).isEqualTo(EmbeddingInputType.TEXT);
        assertThat(request.metadata()).isEmpty();
    }

    @Test
    void carriesOptionalProviderModelInputTypeAndMetadata() {
        EmbeddingRequest request = new EmbeddingRequest(
                List.of("table text"),
                "google",
                "gemini-embedding-001",
                EmbeddingInputType.TABLE_TEXT,
                Map.of("source", "table"));

        assertThat(request.provider()).isEqualTo("google");
        assertThat(request.model()).isEqualTo("gemini-embedding-001");
        assertThat(request.inputType()).isEqualTo(EmbeddingInputType.TABLE_TEXT);
        assertThat(request.metadata()).containsEntry("source", "table");
    }
}
