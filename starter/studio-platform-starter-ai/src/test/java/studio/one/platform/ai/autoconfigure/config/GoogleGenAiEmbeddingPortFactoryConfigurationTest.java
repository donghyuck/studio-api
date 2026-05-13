package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class GoogleGenAiEmbeddingPortFactoryConfigurationTest {

    @Test
    void shouldRejectGeminiEmbeddingBaseUrlOverride() {
        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.setApiKey("test-key");
        provider.setBaseUrl("https://proxy.example.com");
        provider.getEmbedding().setModel("text-embedding-004");

        StandardEnvironment environment = new StandardEnvironment();

        GoogleGenAiEmbeddingPortFactoryConfiguration.GoogleGenAiEmbeddingPortFactory factory =
                new GoogleGenAiEmbeddingPortFactoryConfiguration.GoogleGenAiEmbeddingPortFactory();

        assertThatThrownBy(() -> factory.create("gemini", provider, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("embedding base-url override is not supported");
    }

    @Test
    void shouldRejectLegacyGeminiEmbeddingBaseUrlOverride() {
        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.setApiKey("test-key");
        provider.getEmbedding().setModel("text-embedding-004");

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.ai.google.genai.embedding.text.base-url", "https://proxy.example.com")));

        GoogleGenAiEmbeddingPortFactoryConfiguration.GoogleGenAiEmbeddingPortFactory factory =
                new GoogleGenAiEmbeddingPortFactoryConfiguration.GoogleGenAiEmbeddingPortFactory();

        assertThatThrownBy(() -> factory.create("gemini", provider, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("embedding base-url override is not supported");
    }
}
