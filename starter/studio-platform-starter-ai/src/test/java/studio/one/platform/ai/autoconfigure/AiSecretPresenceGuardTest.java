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

    private static Environment environment() {
        return new MockEnvironment();
    }

    private static <T> ObjectProvider<T> emptyBeanProvider(Class<T> type) {
        return new StaticListableBeanFactory().getBeanProvider(type);
    }
}
