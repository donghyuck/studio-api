package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;

/**
 * Verifies that AiProviderRegistryConfiguration fails fast when configured
 * default providers have no registered port (library missing or provider disabled).
 */
class AiProviderRegistryConfigurationTest {

    @Test
    void registryCreationFailsWhenLegacyDefaultProviderHasNoChatPort() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("openai");

        AiProviderRegistryConfiguration configuration =
                new AiProviderRegistryConfiguration(new StaticListableBeanFactory().getBeanProvider(
                        studio.one.platform.service.I18n.class));

        assertThatThrownBy(() -> configuration.aiProviderRegistry(
                properties,
                Map.of(),        // no chat ports — factory was not registered
                Map.of()))       // no embedding ports
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openai")
                .hasMessageContaining("studio.ai.default-chat-provider");
    }

    @Test
    void registryCreationSucceedsWhenLegacyDefaultProviderHasChatAndEmbeddingPorts() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("openai");

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        AiProviderRegistryConfiguration configuration =
                new AiProviderRegistryConfiguration(beanFactory.getBeanProvider(
                        studio.one.platform.service.I18n.class));

        ChatPort mockChatPort = org.mockito.Mockito.mock(ChatPort.class);
        EmbeddingPort mockEmbeddingPort = org.mockito.Mockito.mock(EmbeddingPort.class);

        AiProviderRegistry registry = configuration.aiProviderRegistry(
                properties,
                Map.of("openai", mockChatPort),
                Map.of("openai", mockEmbeddingPort));

        assertThat(registry.defaultProvider()).isEqualTo("openai");
        assertThat(registry.defaultChatProvider()).isEqualTo("openai");
        assertThat(registry.defaultEmbeddingProvider()).isEqualTo("openai");
        assertThat(registry.chatPort(null)).isSameAs(mockChatPort);
        assertThat(registry.embeddingPort(null)).isSameAs(mockEmbeddingPort);
    }

    @Test
    void registryCreationSucceedsWhenChatAndEmbeddingDefaultsAreSplit() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultChatProvider("openai");
        properties.setDefaultEmbeddingProvider("ollama");

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        AiProviderRegistryConfiguration configuration =
                new AiProviderRegistryConfiguration(beanFactory.getBeanProvider(
                        studio.one.platform.service.I18n.class));

        ChatPort mockChatPort = org.mockito.Mockito.mock(ChatPort.class);
        EmbeddingPort mockEmbeddingPort = org.mockito.Mockito.mock(EmbeddingPort.class);

        AiProviderRegistry registry = configuration.aiProviderRegistry(
                properties,
                Map.of("openai", mockChatPort),
                Map.of("ollama", mockEmbeddingPort));

        assertThat(registry.defaultProvider()).isEqualTo("openai");
        assertThat(registry.defaultChatProvider()).isEqualTo("openai");
        assertThat(registry.defaultEmbeddingProvider()).isEqualTo("ollama");
        assertThat(registry.chatPort(null)).isSameAs(mockChatPort);
        assertThat(registry.embeddingPort(null)).isSameAs(mockEmbeddingPort);
    }

    @Test
    void registryCreationFailsWhenSplitEmbeddingDefaultHasNoEmbeddingPort() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultChatProvider("openai");
        properties.setDefaultEmbeddingProvider("ollama");

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        AiProviderRegistryConfiguration configuration =
                new AiProviderRegistryConfiguration(beanFactory.getBeanProvider(
                        studio.one.platform.service.I18n.class));

        ChatPort mockChatPort = org.mockito.Mockito.mock(ChatPort.class);

        assertThatThrownBy(() -> configuration.aiProviderRegistry(
                properties,
                Map.of("openai", mockChatPort),
                Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ollama")
                .hasMessageContaining("studio.ai.default-embedding-provider");
    }
}
