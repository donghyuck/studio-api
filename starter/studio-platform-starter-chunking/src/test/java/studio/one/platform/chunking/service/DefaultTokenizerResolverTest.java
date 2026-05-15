package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.autoconfigure.ChunkingProperties;
import studio.one.platform.chunking.core.ChunkMetadata;

class DefaultTokenizerResolverTest {

    @Test
    void resolvesOpenAiEmbeddingModelsToCl100k() {
        DefaultTokenizerResolver resolver = resolver(new ChunkingProperties.TokenizerProperties());

        var resolved = resolver.resolve(Map.of("embeddingProvider", "openai", "embeddingModel", "text-embedding-3-small"));

        assertThat(resolved.provider()).isEqualTo("tiktoken");
        assertThat(resolved.encoding()).isEqualTo("cl100k_base");
        assertThat(resolved.selectionSource()).isEqualTo("model-mapping");
        assertThat(resolved.fallbackUsed()).isFalse();
    }

    @Test
    void resolvesGpt4oModelsToO200k() {
        DefaultTokenizerResolver resolver = resolver(new ChunkingProperties.TokenizerProperties());

        var resolved = resolver.resolve(Map.of("embeddingProvider", "openai", "embeddingModel", "gpt-4o-mini"));

        assertThat(resolved.encoding()).isEqualTo("o200k_base");
    }

    @Test
    void explicitMetadataOverridesModelMapping() {
        DefaultTokenizerResolver resolver = resolver(new ChunkingProperties.TokenizerProperties());

        var resolved = resolver.resolve(Map.of(
                "embeddingModel", "text-embedding-3-small",
                ChunkMetadata.KEY_TOKENIZER_PROVIDER, "tiktoken",
                ChunkMetadata.KEY_TOKENIZER_ENCODING, "p50k_base"));

        assertThat(resolved.encoding()).isEqualTo("p50k_base");
        assertThat(resolved.selectionSource()).isEqualTo("explicit-config");
    }

    @Test
    void metadataCanDisableAutoDetectForModelAndProviderMapping() {
        DefaultTokenizerResolver resolver = resolver(new ChunkingProperties.TokenizerProperties());

        var resolved = resolver.resolve(Map.of(
                "embeddingProvider", "openai",
                "embeddingModel", "text-embedding-3-small",
                "tokenizerAutoDetect", false));

        assertThat(resolved.provider()).isEqualTo("approximate");
        assertThat(resolved.selectionSource()).isEqualTo("fallback");
        assertThat(resolved.fallbackUsed()).isTrue();
    }

    @Test
    void unknownModelFallsBackToApproximateTokenizer() {
        DefaultTokenizerResolver resolver = resolver(new ChunkingProperties.TokenizerProperties());

        var resolved = resolver.resolve(Map.of("embeddingModel", "custom-korean-embedding"));

        assertThat(resolved.provider()).isEqualTo("approximate");
        assertThat(resolved.confidence()).isEqualTo("low");
        assertThat(resolved.fallbackUsed()).isTrue();
        assertThat(resolved.warnings()).isNotEmpty();
    }

    @Test
    void failOnUnknownModelRejectsFallback() {
        ChunkingProperties.TokenizerProperties properties = new ChunkingProperties.TokenizerProperties();
        properties.setFailOnUnknownModel(true);
        DefaultTokenizerResolver resolver = resolver(properties);

        assertThatThrownBy(() -> resolver.resolve(Map.of("embeddingModel", "custom-korean-embedding")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tokenizer mapping is not available");
    }

    private DefaultTokenizerResolver resolver(ChunkingProperties.TokenizerProperties properties) {
        return new DefaultTokenizerResolver(properties, List.of(new ApproximateTokenizer()));
    }
}
