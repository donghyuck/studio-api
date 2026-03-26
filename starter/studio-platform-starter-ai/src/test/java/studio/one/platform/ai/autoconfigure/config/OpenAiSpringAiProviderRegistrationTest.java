package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;

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

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("springAiChatModel", org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class));
        beanFactory.addBean("springAiEmbeddingModel", org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.openai.api-key", "test-key")
                .withProperty("spring.ai.openai.chat.options.model", "gpt-4o-mini")
                .withProperty("spring.ai.openai.embedding.options.model", "text-embedding-3-small");

        ProviderChatPortFactory chatFactory = new OpenAiPortFactoryConfiguration().openAiChatPortFactory();
        ProviderEmbeddingPortFactory embeddingFactory = new OpenAiPortFactoryConfiguration().openAiEmbeddingPortFactory();

        Map<String, ChatPort> chatPorts = new ProviderChatConfiguration().chatPorts(
                properties,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                List.of(chatFactory));
        Map<String, EmbeddingPort> embeddingPorts = new ProviderEmbeddingConfiguration().embeddingPorts(
                properties,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class),
                List.of(embeddingFactory));

        assertThat(chatPorts).containsOnlyKeys("openai");
        assertThat(embeddingPorts).containsOnlyKeys("openai");

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
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.openai.api-key", "test-key")
                .withProperty("spring.ai.openai.chat.options.model", "gpt-4o-mini");

        List<ProviderChatPortFactory> factories = List.of(
                new OpenAiPortFactoryConfiguration().openAiChatPortFactory(),
                new GoogleGenAiChatPortFactoryConfiguration().googleGenAiChatPortFactory());

        Map<String, ChatPort> chatPorts = new ProviderChatConfiguration().chatPorts(
                properties,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                factories);

        assertThat(chatPorts).containsOnlyKeys("openai", "google");
    }
}
