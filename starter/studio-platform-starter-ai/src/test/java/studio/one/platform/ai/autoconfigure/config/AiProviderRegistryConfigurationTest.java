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
 * Verifies that AiProviderRegistryConfiguration fails fast when the configured
 * default-provider has no registered port (library missing or provider disabled).
 */
class AiProviderRegistryConfigurationTest {

    @Test
    void registryCreationFailsWhenDefaultProviderHasNoChatOrEmbeddingPort() {
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
                .hasMessageContaining("studio.ai.default-provider");
    }

    @Test
    void registryCreationSucceedsWhenDefaultProviderHasChatPort() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("openai");

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        AiProviderRegistryConfiguration configuration =
                new AiProviderRegistryConfiguration(beanFactory.getBeanProvider(
                        studio.one.platform.service.I18n.class));

        ChatPort mockChatPort = org.mockito.Mockito.mock(ChatPort.class);

        AiProviderRegistry registry = configuration.aiProviderRegistry(
                properties,
                Map.of("openai", mockChatPort),
                Map.of());

        assertThat(registry.defaultProvider()).isEqualTo("openai");
        assertThat(registry.chatPort(null)).isSameAs(mockChatPort);
    }

    @Test
    void registryCreationSucceedsWhenDefaultProviderHasEmbeddingPortOnly() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("ollama");

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        AiProviderRegistryConfiguration configuration =
                new AiProviderRegistryConfiguration(beanFactory.getBeanProvider(
                        studio.one.platform.service.I18n.class));

        EmbeddingPort mockEmbeddingPort = org.mockito.Mockito.mock(EmbeddingPort.class);

        AiProviderRegistry registry = configuration.aiProviderRegistry(
                properties,
                Map.of(),
                Map.of("ollama", mockEmbeddingPort));

        assertThat(registry.defaultProvider()).isEqualTo("ollama");
        assertThat(registry.embeddingPort(null)).isSameAs(mockEmbeddingPort);
    }
}
