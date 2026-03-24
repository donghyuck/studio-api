package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.service.I18n;

class SpringAiAliasProviderRegistrationTest {

    @Test
    void registersSpringAiAliasProvidersForOpenAiWhenEnabled() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("openai");
        properties.getSpringAi().setEnabled(true);
        properties.getSpringAi().setSourceProvider("openai");
        properties.getSpringAi().setProviderSuffix("-springai");

        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.OPENAI);
        provider.setApiKey("test-key");
        provider.getChat().setEnabled(true);
        provider.getChat().setModel("gpt-4o-mini");
        provider.getEmbedding().setEnabled(true);
        provider.getEmbedding().setModel("text-embedding-3-small");
        properties.getProviders().put("openai", provider);

        LangChainChatConfiguration chatConfiguration = new LangChainChatConfiguration();
        LangChainEmbeddingConfiguration embeddingConfiguration = new LangChainEmbeddingConfiguration();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("springAiChatModel", org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class));
        beanFactory.addBean("springAiEmbeddingModel", org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class));
        ObjectProvider<I18n> i18nProvider = beanFactory.getBeanProvider(I18n.class);

        Map<String, ChatPort> chatPorts = chatConfiguration.chatPorts(properties, i18nProvider,
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class));
        Map<String, EmbeddingPort> embeddingPorts = embeddingConfiguration.embeddingPorts(properties, i18nProvider,
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class));

        assertThat(chatPorts).containsKeys("openai", "openai-springai");
        assertThat(embeddingPorts).containsKeys("openai", "openai-springai");
        assertThat(chatPorts.get("openai")).isNotSameAs(chatPorts.get("openai-springai"));
        assertThat(embeddingPorts.get("openai")).isNotSameAs(embeddingPorts.get("openai-springai"));

        AiProviderRegistry registry = new AiProviderRegistry("openai-springai", chatPorts, embeddingPorts);
        assertThat(registry.defaultProvider()).isEqualTo("openai-springai");
        assertThat(registry.chatPort(null)).isSameAs(chatPorts.get("openai-springai"));
        assertThat(registry.embeddingPort(null)).isSameAs(embeddingPorts.get("openai-springai"));
    }

    @Test
    void rejectsBlankSpringAiProviderSuffix() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("openai");
        properties.getSpringAi().setEnabled(true);
        properties.getSpringAi().setSourceProvider("openai");
        properties.getSpringAi().setProviderSuffix(" ");

        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.OPENAI);
        provider.setApiKey("test-key");
        provider.getChat().setEnabled(true);
        provider.getChat().setModel("gpt-4o-mini");
        properties.getProviders().put("openai", provider);

        LangChainChatConfiguration chatConfiguration = new LangChainChatConfiguration();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("springAiChatModel", org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class));
        ObjectProvider<I18n> i18nProvider = beanFactory.getBeanProvider(I18n.class);

        assertThatThrownBy(() -> chatConfiguration.chatPorts(properties, i18nProvider,
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider-suffix");
    }

    @Test
    void rejectsAliasCollisionWithExistingProviderName() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("openai");
        properties.getSpringAi().setEnabled(true);
        properties.getSpringAi().setSourceProvider("openai");
        properties.getSpringAi().setProviderSuffix("-springai");

        AiAdapterProperties.Provider openAi = new AiAdapterProperties.Provider();
        openAi.setType(AiAdapterProperties.ProviderType.OPENAI);
        openAi.setApiKey("test-key");
        openAi.getChat().setEnabled(true);
        openAi.getChat().setModel("gpt-4o-mini");
        properties.getProviders().put("openai", openAi);

        AiAdapterProperties.Provider colliding = new AiAdapterProperties.Provider();
        colliding.setType(AiAdapterProperties.ProviderType.OPENAI);
        colliding.setApiKey("test-key");
        colliding.getChat().setEnabled(true);
        colliding.getChat().setModel("gpt-4o-mini");
        properties.getProviders().put("openai-springai", colliding);

        LangChainChatConfiguration chatConfiguration = new LangChainChatConfiguration();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("springAiChatModel", org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class));
        ObjectProvider<I18n> i18nProvider = beanFactory.getBeanProvider(I18n.class);

        assertThatThrownBy(() -> chatConfiguration.chatPorts(properties, i18nProvider,
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("collides");
    }

    @Test
    void registersAliasOnlyForConfiguredSpringAiSourceProvider() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("openai");
        properties.getSpringAi().setEnabled(true);
        properties.getSpringAi().setProviderSuffix("-springai");
        properties.getSpringAi().setSourceProvider("openai");

        AiAdapterProperties.Provider openAi = new AiAdapterProperties.Provider();
        openAi.setType(AiAdapterProperties.ProviderType.OPENAI);
        openAi.setApiKey("test-key");
        openAi.getChat().setEnabled(true);
        openAi.getChat().setModel("gpt-4o-mini");
        properties.getProviders().put("openai", openAi);

        AiAdapterProperties.Provider secondOpenAi = new AiAdapterProperties.Provider();
        secondOpenAi.setType(AiAdapterProperties.ProviderType.OPENAI);
        secondOpenAi.setApiKey("test-key");
        secondOpenAi.getChat().setEnabled(true);
        secondOpenAi.getChat().setModel("gpt-4.1-mini");
        properties.getProviders().put("backup-openai", secondOpenAi);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("springAiChatModel", org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class));
        ObjectProvider<I18n> i18nProvider = beanFactory.getBeanProvider(I18n.class);

        Map<String, ChatPort> chatPorts = new LangChainChatConfiguration().chatPorts(properties, i18nProvider,
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class));

        assertThat(chatPorts).containsKeys("openai", "openai-springai", "backup-openai");
        assertThat(chatPorts).doesNotContainKey("backup-openai-springai");
    }
}
