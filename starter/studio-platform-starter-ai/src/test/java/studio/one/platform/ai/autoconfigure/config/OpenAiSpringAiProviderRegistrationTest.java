package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.autoconfigure.adapter.SpringAiChatAdapter;
import studio.one.platform.ai.autoconfigure.adapter.SpringAiEmbeddingAdapter;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.service.I18n;

class OpenAiSpringAiProviderRegistrationTest {

    @Test
    void registersOpenAiProviderAsSingleSpringAiBackedPath() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("openai");

        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.OPENAI);
        provider.getChat().setEnabled(true);
        provider.getEmbedding().setEnabled(true);
        properties.getProviders().put("openai", provider);

        ProviderChatConfiguration chatConfiguration = new ProviderChatConfiguration();
        ProviderEmbeddingConfiguration embeddingConfiguration = new ProviderEmbeddingConfiguration();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("springAiChatModel", org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class));
        beanFactory.addBean("springAiEmbeddingModel", org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class));
        ObjectProvider<I18n> i18nProvider = beanFactory.getBeanProvider(I18n.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.openai.api-key", "test-key")
                .withProperty("spring.ai.openai.chat.options.model", "gpt-4o-mini")
                .withProperty("spring.ai.openai.embedding.options.model", "text-embedding-3-small");

        Map<String, ChatPort> chatPorts = chatConfiguration.chatPorts(properties, i18nProvider,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class));
        Map<String, EmbeddingPort> embeddingPorts = embeddingConfiguration.embeddingPorts(properties, i18nProvider,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class));

        assertThat(chatPorts).containsOnlyKeys("openai");
        assertThat(embeddingPorts).containsOnlyKeys("openai");
        assertThat(chatPorts.get("openai")).isInstanceOf(SpringAiChatAdapter.class);
        assertThat(embeddingPorts.get("openai")).isInstanceOf(SpringAiEmbeddingAdapter.class);

        AiProviderRegistry registry = new AiProviderRegistry("openai", chatPorts, embeddingPorts);
        assertThat(registry.defaultProvider()).isEqualTo("openai");
        assertThat(registry.chatPort(null)).isSameAs(chatPorts.get("openai"));
        assertThat(registry.embeddingPort(null)).isSameAs(embeddingPorts.get("openai"));
    }

    @Test
    void stillRegistersOnlyOneOpenAiProviderWhenAdditionalProvidersExist() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("openai");

        AiAdapterProperties.Provider openAi = new AiAdapterProperties.Provider();
        openAi.setType(AiAdapterProperties.ProviderType.OPENAI);
        openAi.getChat().setEnabled(true);
        properties.getProviders().put("openai", openAi);

        AiAdapterProperties.Provider google = new AiAdapterProperties.Provider();
        google.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        google.setApiKey("test-key");
        google.getChat().setEnabled(true);
        google.getChat().setModel("gemini-1.5-flash");
        properties.getProviders().put("google", google);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("springAiChatModel", org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class));
        ObjectProvider<I18n> i18nProvider = beanFactory.getBeanProvider(I18n.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.openai.api-key", "test-key")
                .withProperty("spring.ai.openai.chat.options.model", "gpt-4o-mini");

        Map<String, ChatPort> chatPorts = new ProviderChatConfiguration().chatPorts(
                properties,
                i18nProvider,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class));

        assertThat(chatPorts).containsOnlyKeys("openai", "google");
        assertThat(chatPorts.get("openai")).isInstanceOf(SpringAiChatAdapter.class);
        assertThat(chatPorts.get("google")).isInstanceOf(SpringAiChatAdapter.class);
    }

    @Test
    void registersGoogleAndOllamaEmbeddingsAsSpringAiBackedPaths() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("google");

        AiAdapterProperties.Provider google = new AiAdapterProperties.Provider();
        google.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        google.setApiKey("test-key");
        google.getEmbedding().setEnabled(true);
        google.getEmbedding().setModel("text-embedding-004");
        properties.getProviders().put("google", google);

        AiAdapterProperties.Provider ollama = new AiAdapterProperties.Provider();
        ollama.setType(AiAdapterProperties.ProviderType.OLLAMA);
        ollama.getEmbedding().setEnabled(true);
        ollama.getEmbedding().setModel("nomic-embed-text");
        properties.getProviders().put("ollama", ollama);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        ObjectProvider<I18n> i18nProvider = beanFactory.getBeanProvider(I18n.class);
        MockEnvironment environment = new MockEnvironment();

        Map<String, EmbeddingPort> embeddingPorts = new ProviderEmbeddingConfiguration().embeddingPorts(
                properties,
                i18nProvider,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class));

        assertThat(embeddingPorts).containsOnlyKeys("google", "ollama");
        assertThat(embeddingPorts.get("google")).isInstanceOf(SpringAiEmbeddingAdapter.class);
        assertThat(embeddingPorts.get("ollama")).isInstanceOf(SpringAiEmbeddingAdapter.class);
    }
}
