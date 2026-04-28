package studio.one.platform.ai.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties.Provider;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties.ProviderType;

class AiSecretPresenceGuardTest {

    @Test
    void validateRequiresDefaultProvider() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);

        AiSecretPresenceGuard guard = guard(properties, environment());

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateAllowsSplitDefaultProvidersWithoutLegacyDefaultProvider() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.setDefaultChatProvider("google-chat");
        properties.setDefaultEmbeddingProvider("google-embedding");

        Provider chatProvider = new Provider();
        chatProvider.setEnabled(true);
        chatProvider.setType(ProviderType.GOOGLE_AI_GEMINI);
        chatProvider.getChat().setEnabled(true);
        properties.getProviders().put("google-chat", chatProvider);

        Provider embeddingProvider = new Provider();
        embeddingProvider.setEnabled(true);
        embeddingProvider.setType(ProviderType.GOOGLE_AI_GEMINI);
        embeddingProvider.getEmbedding().setEnabled(true);
        properties.getProviders().put("google-embedding", embeddingProvider);

        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.google.genai.chat.api-key", "test-key");
        environment.setProperty("spring.ai.google.genai.chat.options.model", "gemini-2.5-flash");
        environment.setProperty("spring.ai.google.genai.embedding.api-key", "test-key");
        environment.setProperty("spring.ai.google.genai.embedding.text.options.model", "gemini-embedding-001");

        AiSecretPresenceGuard guard = guard(properties, environment);

        assertDoesNotThrow(guard::validate);
    }

    @Test
    void validateRequiresSpringAiApiKeyForOpenAiProvider() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.setDefaultProvider("openai");

        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.OPENAI);
        provider.getChat().setEnabled(true);
        properties.getProviders().put("openai", provider);

        AiSecretPresenceGuard guard = guard(properties, environment());

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateRequiresSpringAiChatModelPropertyForEnabledOpenAiChat() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.setDefaultProvider("openai");

        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.OPENAI);
        provider.getChat().setEnabled(true);
        properties.getProviders().put("openai", provider);

        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.openai.api-key", "test-key");
        // intentionally omit spring.ai.openai.chat.options.model

        AiSecretPresenceGuard guard = guard(properties, environment);

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateAllowsOpenAiProviderWithFullSpringAiProperties() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.setDefaultProvider("openai");

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

        AiSecretPresenceGuard guard = guard(properties, environment);

        assertDoesNotThrow(guard::validate);
    }

    @Test
    void validateAllowsConfiguredSpringAiPropertiesForOllamaEmbedding() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.setDefaultProvider("ollama");
        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.OLLAMA);
        provider.getEmbedding().setEnabled(true);
        properties.getProviders().put("ollama", provider);

        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.ollama.embedding.options.model", "nomic-embed-text");

        AiSecretPresenceGuard guard = guard(properties, environment);

        assertDoesNotThrow(guard::validate);
    }

    @Test
    void validateRejectsMissingModelPropertyForOllamaEmbedding() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.setDefaultProvider("ollama");
        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.OLLAMA);
        provider.getEmbedding().setEnabled(true);
        properties.getProviders().put("ollama", provider);

        AiSecretPresenceGuard guard = guard(properties, environment());

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateAllowsConfiguredSpringAiPropertiesForGoogleEmbedding() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.setDefaultProvider("google");
        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.GOOGLE_AI_GEMINI);
        provider.getEmbedding().setEnabled(true);
        properties.getProviders().put("google", provider);

        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.google.genai.embedding.api-key", "test-key");
        environment.setProperty("spring.ai.google.genai.embedding.text.options.model", "gemini-embedding-001");

        AiSecretPresenceGuard guard = guard(properties, environment);

        assertDoesNotThrow(guard::validate);
    }

    @Test
    void validateRejectsMissingModelPropertyForGoogleEmbedding() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.setDefaultProvider("google");
        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.GOOGLE_AI_GEMINI);
        provider.getEmbedding().setEnabled(true);
        properties.getProviders().put("google", provider);

        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.google.genai.embedding.api-key", "test-key");
        // intentionally omit text.options.model

        AiSecretPresenceGuard guard = guard(properties, environment);

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateRejectsMissingChatModelForGoogleChat() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.setDefaultProvider("google");
        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.GOOGLE_AI_GEMINI);
        provider.getChat().setEnabled(true);
        properties.getProviders().put("google", provider);

        AiSecretPresenceGuard guard = guard(properties, environment());

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateAllowsConfiguredSpringAiPropertiesForGoogleChat() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.setDefaultProvider("google");
        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.GOOGLE_AI_GEMINI);
        provider.getChat().setEnabled(true);
        properties.getProviders().put("google", provider);

        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.google.genai.chat.api-key", "test-key");
        environment.setProperty("spring.ai.google.genai.chat.options.model", "gemini-2.5-flash");

        AiSecretPresenceGuard guard = guard(properties, environment);

        assertDoesNotThrow(guard::validate);
    }

    private static Environment environment() {
        return new MockEnvironment();
    }

    private static AiSecretPresenceGuard guard(AiAdapterProperties properties, Environment environment) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        return new AiSecretPresenceGuard(
                properties,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class));
    }
}
