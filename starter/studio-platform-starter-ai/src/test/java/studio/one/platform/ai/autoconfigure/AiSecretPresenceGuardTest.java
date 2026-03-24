package studio.one.platform.ai.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties.Provider;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties.ProviderType;

class AiSecretPresenceGuardTest {

    @Test
    void validateRejectsMissingApiKeyForOpenAiProvider() {
        AiAdapterProperties properties = new AiAdapterProperties();
        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.OPENAI);
        provider.setApiKey(" ");
        properties.getProviders().put("openai", provider);

        AiSecretPresenceGuard guard = new AiSecretPresenceGuard(properties, environment(),
                emptyBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                emptyBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class));

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateAllowsConfiguredOllamaBaseUrl() {
        AiAdapterProperties properties = new AiAdapterProperties();
        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.OLLAMA);
        provider.setBaseUrl("http://localhost:11434");
        properties.getProviders().put("ollama", provider);

        AiSecretPresenceGuard guard = new AiSecretPresenceGuard(properties, environment(),
                emptyBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                emptyBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class));

        assertDoesNotThrow(guard::validate);
    }

    @Test
    void validateRequiresSpringAiSourceProviderWhenAliasModeIsEnabled() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.getSpringAi().setEnabled(true);

        AiSecretPresenceGuard guard = new AiSecretPresenceGuard(properties, environment(),
                emptyBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                emptyBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class));

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateAllowsMissingStudioApiKeyForSpringAiSourceProvider() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.getSpringAi().setEnabled(true);
        properties.getSpringAi().setSourceProvider("openai");

        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.OPENAI);
        provider.getChat().setEnabled(true);
        provider.getEmbedding().setEnabled(true);
        properties.getProviders().put("openai", provider);

        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.openai.api-key", "test-key");
        environment.setProperty("spring.ai.openai.chat.options.model", "gpt-4o-mini");
        environment.setProperty("spring.ai.openai.embedding.options.model", "text-embedding-3-small");
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("chatModel", org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class));
        beanFactory.addBean("embeddingModel", org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class));

        AiSecretPresenceGuard guard = new AiSecretPresenceGuard(properties, environment,
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class));

        assertDoesNotThrow(guard::validate);
    }

    @Test
    void validateRequiresSpringAiChatModelPropertyForEnabledSourceProviderChat() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.getSpringAi().setEnabled(true);
        properties.getSpringAi().setSourceProvider("openai");

        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.OPENAI);
        provider.getChat().setEnabled(true);
        properties.getProviders().put("openai", provider);

        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.openai.api-key", "test-key");
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("chatModel", org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class));

        AiSecretPresenceGuard guard = new AiSecretPresenceGuard(properties, environment,
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class));

        assertThrows(IllegalStateException.class, guard::validate);
    }

    private static Environment environment() {
        return new MockEnvironment();
    }

    private static <T> ObjectProvider<T> emptyBeanProvider(Class<T> type) {
        return new StaticListableBeanFactory().getBeanProvider(type);
    }
}
