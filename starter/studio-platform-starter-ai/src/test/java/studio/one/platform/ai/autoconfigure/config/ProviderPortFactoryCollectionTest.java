package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

/**
 * Verifies that ProviderChatConfiguration / ProviderEmbeddingConfiguration
 * correctly exclude providers whose factory is not registered (simulating a missing
 * provider library on the classpath) and include only the providers whose factory
 * is present.
 */
class ProviderPortFactoryCollectionTest {

    @Test
    void chatPortsIsEmptyWhenNoFactoryRegistered() {
        AiAdapterProperties properties = new AiAdapterProperties();
        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.OPENAI);
        provider.getChat().setEnabled(true);
        properties.getProviders().put("openai", provider);

        Map<String, ChatPort> ports = new ProviderChatConfiguration().chatPorts(
                properties,
                new MockEnvironment(),
                new StaticListableBeanFactory().getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                List.of()); // no factory registered — simulates library not on classpath

        assertThat(ports).isEmpty();
    }

    @Test
    void embeddingPortsIsEmptyWhenNoFactoryRegistered() {
        AiAdapterProperties properties = new AiAdapterProperties();
        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.OLLAMA);
        provider.getEmbedding().setEnabled(true);
        properties.getProviders().put("ollama", provider);

        Map<String, EmbeddingPort> ports = new ProviderEmbeddingConfiguration().embeddingPorts(
                properties,
                new MockEnvironment(),
                new StaticListableBeanFactory().getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class),
                List.of()); // no factory registered

        assertThat(ports).isEmpty();
    }

    @Test
    void onlyProvidersWithRegisteredFactoryAreIncludedInChatPorts() {
        AiAdapterProperties properties = new AiAdapterProperties();

        AiAdapterProperties.Provider openAi = new AiAdapterProperties.Provider();
        openAi.setType(AiAdapterProperties.ProviderType.OPENAI);
        openAi.getChat().setEnabled(true);
        properties.getProviders().put("openai", openAi);

        AiAdapterProperties.Provider google = new AiAdapterProperties.Provider();
        google.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        google.getChat().setEnabled(true);
        google.setApiKey("test-key");
        google.getChat().setModel("gemini-2.5-flash");
        properties.getProviders().put("google", google);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("chatModel", org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class));

        // Only Google factory registered — OpenAI is absent (simulates missing library)
        Map<String, ChatPort> ports = new ProviderChatConfiguration().chatPorts(
                properties,
                new MockEnvironment(),
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                List.of(new GoogleGenAiChatPortFactoryConfiguration().googleGenAiChatPortFactory()));

        assertThat(ports).containsOnlyKeys("google");
    }

    @Test
    void onlyProvidersWithRegisteredFactoryAreIncludedInEmbeddingPorts() {
        AiAdapterProperties properties = new AiAdapterProperties();

        AiAdapterProperties.Provider openAi = new AiAdapterProperties.Provider();
        openAi.setType(AiAdapterProperties.ProviderType.OPENAI);
        openAi.getEmbedding().setEnabled(true);
        properties.getProviders().put("openai", openAi);

        AiAdapterProperties.Provider ollama = new AiAdapterProperties.Provider();
        ollama.setType(AiAdapterProperties.ProviderType.OLLAMA);
        ollama.getEmbedding().setEnabled(true);
        properties.getProviders().put("ollama", ollama);

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.ollama.embedding.options.model", "nomic-embed-text");

        // Only Ollama factory registered — OpenAI is absent
        Map<String, EmbeddingPort> ports = new ProviderEmbeddingConfiguration().embeddingPorts(
                properties,
                environment,
                new StaticListableBeanFactory().getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class),
                List.of(new OllamaPortFactoryConfiguration().ollamaEmbeddingPortFactory()));

        assertThat(ports).containsOnlyKeys("ollama");
    }

    @Test
    void disabledProviderIsExcludedEvenWhenFactoryIsRegistered() {
        AiAdapterProperties properties = new AiAdapterProperties();
        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.OLLAMA);
        provider.setEnabled(false); // explicitly disabled
        provider.getEmbedding().setEnabled(true);
        properties.getProviders().put("ollama", provider);

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.ollama.embedding.options.model", "nomic-embed-text");

        Map<String, EmbeddingPort> ports = new ProviderEmbeddingConfiguration().embeddingPorts(
                properties,
                environment,
                new StaticListableBeanFactory().getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class),
                List.of(new OllamaPortFactoryConfiguration().ollamaEmbeddingPortFactory()));

        assertThat(ports).isEmpty();
    }
}
