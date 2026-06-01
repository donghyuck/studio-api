package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;

class DefaultAiEmbeddingOptionCatalogTest {

    @Test
    void listsRegisteredEmbeddingProvidersWithResolvedModelMetadata() {
        AiAdapterProperties properties = new AiAdapterProperties();
        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.OLLAMA);
        provider.getEmbedding().setEnabled(true);
        provider.getEmbedding().setModel("nomic-embed-text");
        provider.getEmbedding().setDimension(768);
        properties.getProviders().put("local", provider);

        AiProviderRegistry registry = new AiProviderRegistry(
                "local",
                Map.of("local", org.mockito.Mockito.mock(studio.one.platform.ai.core.chat.ChatPort.class)),
                Map.of("local", org.mockito.Mockito.mock(EmbeddingPort.class)));

        DefaultAiEmbeddingOptionCatalog catalog = new DefaultAiEmbeddingOptionCatalog(
                registry,
                properties,
                new RagEmbeddingProperties(),
                new MockEnvironment());

        assertThat(catalog.options())
                .singleElement()
                .satisfies(option -> {
                    assertThat(option.provider()).isEqualTo("local");
                    assertThat(option.providerType()).isEqualTo("OLLAMA");
                    assertThat(option.model()).isEqualTo("nomic-embed-text");
                    assertThat(option.dimension()).isEqualTo(768);
                    assertThat(option.defaultProvider()).isTrue();
                    assertThat(option.profile()).isFalse();
                    assertThat(option.source()).isEqualTo("provider");
                });
    }

    @Test
    void addsRagProfilesAsSelectableEmbeddingOptions() {
        AiAdapterProperties properties = new AiAdapterProperties();
        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.getEmbedding().setEnabled(true);
        properties.getProviders().put("google", provider);

        RagEmbeddingProperties ragProperties = new RagEmbeddingProperties();
        ragProperties.setDefaultEmbeddingProfile("retrieval-ko");
        RagEmbeddingProperties.ProfileProperties profile = new RagEmbeddingProperties.ProfileProperties();
        profile.setProvider("google");
        profile.setModel("text-embedding-004");
        profile.setDimension(768);
        profile.setSupportedInputTypes(java.util.List.of("text", "ocr-text"));
        profile.setMetadata(Map.of("locale", "ko"));
        ragProperties.getEmbeddingProfiles().put("retrieval-ko", profile);

        AiProviderRegistry registry = new AiProviderRegistry(
                "google",
                Map.of("google", org.mockito.Mockito.mock(studio.one.platform.ai.core.chat.ChatPort.class)),
                Map.of("google", org.mockito.Mockito.mock(EmbeddingPort.class)));

        DefaultAiEmbeddingOptionCatalog catalog = new DefaultAiEmbeddingOptionCatalog(
                registry,
                properties,
                ragProperties,
                new MockEnvironment());

        assertThat(catalog.options())
                .hasSize(2)
                .anySatisfy(option -> {
                    assertThat(option.profileId()).isEqualTo("retrieval-ko");
                    assertThat(option.provider()).isEqualTo("google");
                    assertThat(option.model()).isEqualTo("text-embedding-004");
                    assertThat(option.dimension()).isEqualTo(768);
                    assertThat(option.supportedInputTypes()).containsExactly("TEXT", "OCR_TEXT");
                    assertThat(option.defaultProfile()).isTrue();
                    assertThat(option.profile()).isTrue();
                    assertThat(option.source()).isEqualTo("rag-profile");
                    assertThat(option.metadata()).containsEntry("locale", "ko");
                });
    }

    @Test
    void environmentProviderModelOverridesLegacyProviderMetadata() {
        AiAdapterProperties properties = new AiAdapterProperties();
        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.OPENAI);
        provider.getEmbedding().setEnabled(true);
        provider.getEmbedding().setModel("legacy-model");
        properties.getProviders().put("openai", provider);

        AiProviderRegistry registry = new AiProviderRegistry(
                "openai",
                Map.of("openai", org.mockito.Mockito.mock(studio.one.platform.ai.core.chat.ChatPort.class)),
                Map.of("openai", org.mockito.Mockito.mock(EmbeddingPort.class)));

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.openai.embedding.options.model", "text-embedding-3-small");

        DefaultAiEmbeddingOptionCatalog catalog = new DefaultAiEmbeddingOptionCatalog(
                registry,
                properties,
                new RagEmbeddingProperties(),
                environment);

        assertThat(catalog.options())
                .singleElement()
                .extracting(AiEmbeddingOption::model)
                .isEqualTo("text-embedding-3-small");
    }
}
