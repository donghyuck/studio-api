package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;

/**
 * Verifies that AiProviderRegistryConfiguration fails fast when configured
 * default providers have no registered port (library missing or provider disabled).
 */
class AiProviderRegistryConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ProviderChatConfiguration.class,
                    ProviderEmbeddingConfiguration.class,
                    AiProviderRegistryConfiguration.class))
            .withBean(studio.one.platform.service.I18n.class, () -> (code, args, locale) -> code)
            .withBean(ProviderChatPortFactory.class, () -> new TestChatPortFactory())
            .withBean(ProviderEmbeddingPortFactory.class, () -> new TestEmbeddingPortFactory());

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
                .hasMessageContaining("studio.ai.default-provider")
                .hasMessageContaining("ChatPort");
    }

    @Test
    void registryCreationFailsWhenLegacyDefaultProviderHasOnlyChatPort() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("openai");

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
                .hasMessageContaining("openai")
                .hasMessageContaining("studio.ai.default-provider")
                .hasMessageContaining("EmbeddingPort");
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
    void registryCreationSucceedsWhenSplitDefaultsOverrideValidLegacyDefaultProvider() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("openai");
        properties.setDefaultChatProvider("google");
        properties.setDefaultEmbeddingProvider("ollama");

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        AiProviderRegistryConfiguration configuration =
                new AiProviderRegistryConfiguration(beanFactory.getBeanProvider(
                        studio.one.platform.service.I18n.class));

        ChatPort openAiChatPort = org.mockito.Mockito.mock(ChatPort.class);
        EmbeddingPort openAiEmbeddingPort = org.mockito.Mockito.mock(EmbeddingPort.class);
        ChatPort googleChatPort = org.mockito.Mockito.mock(ChatPort.class);
        EmbeddingPort ollamaEmbeddingPort = org.mockito.Mockito.mock(EmbeddingPort.class);

        AiProviderRegistry registry = configuration.aiProviderRegistry(
                properties,
                Map.of("openai", openAiChatPort, "google", googleChatPort),
                Map.of("openai", openAiEmbeddingPort, "ollama", ollamaEmbeddingPort));

        assertThat(registry.defaultProvider()).isEqualTo("openai");
        assertThat(registry.defaultChatProvider()).isEqualTo("google");
        assertThat(registry.defaultEmbeddingProvider()).isEqualTo("ollama");
        assertThat(registry.chatPort(null)).isSameAs(googleChatPort);
        assertThat(registry.embeddingPort(null)).isSameAs(ollamaEmbeddingPort);
        assertThat(registry.chatPort(registry.defaultProvider())).isSameAs(openAiChatPort);
        assertThat(registry.embeddingPort(registry.defaultProvider())).isSameAs(openAiEmbeddingPort);
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

    @Test
    void bindsSplitDefaultProvidersInApplicationContext() {
        contextRunner
                .withPropertyValues(
                        "studio.ai.default-chat-provider=chat-provider",
                        "studio.ai.default-embedding-provider=embedding-provider",
                        "studio.ai.providers.chat-provider.type=OLLAMA",
                        "studio.ai.providers.chat-provider.enabled=true",
                        "studio.ai.providers.chat-provider.chat.enabled=true",
                        "studio.ai.providers.embedding-provider.type=OLLAMA",
                        "studio.ai.providers.embedding-provider.enabled=true",
                        "studio.ai.providers.embedding-provider.embedding.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AiProviderRegistry registry = context.getBean(AiProviderRegistry.class);
                    assertThat(registry.defaultProvider()).isEqualTo("chat-provider");
                    assertThat(registry.defaultChatProvider()).isEqualTo("chat-provider");
                    assertThat(registry.defaultEmbeddingProvider()).isEqualTo("embedding-provider");
                    assertThat(registry.chatPort(null)).isSameAs(registry.chatPort("chat-provider"));
                    assertThat(registry.embeddingPort(null)).isSameAs(registry.embeddingPort("embedding-provider"));
                });
    }

    private static final class TestChatPortFactory implements ProviderChatPortFactory {
        @Override
        public AiAdapterProperties.ProviderType supportedType() {
            return AiAdapterProperties.ProviderType.OLLAMA;
        }

        @Override
        public ChatPort create(
                AiAdapterProperties.Provider provider,
                Environment env,
                org.springframework.beans.factory.ObjectProvider<org.springframework.ai.chat.model.ChatModel> chatModelProvider) {
            return org.mockito.Mockito.mock(ChatPort.class);
        }
    }

    private static final class TestEmbeddingPortFactory implements ProviderEmbeddingPortFactory {
        @Override
        public AiAdapterProperties.ProviderType supportedType() {
            return AiAdapterProperties.ProviderType.OLLAMA;
        }

        @Override
        public EmbeddingPort create(
                AiAdapterProperties.Provider provider,
                Environment env,
                org.springframework.beans.factory.ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModelProvider) {
            return org.mockito.Mockito.mock(EmbeddingPort.class);
        }
    }
}
