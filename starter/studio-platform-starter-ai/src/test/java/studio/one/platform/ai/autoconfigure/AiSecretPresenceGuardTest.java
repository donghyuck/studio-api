package studio.one.platform.ai.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties.Provider;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties.ProviderType;
import studio.one.platform.ai.autoconfigure.config.ModelDeploymentProperties;

class AiSecretPresenceGuardTest {

    @Test
    void deploymentModeAllowsConnectionProvidersWithoutDuplicatedModels() {
        AiAdapterProperties properties = new AiAdapterProperties();

        Provider google = new Provider();
        google.setType(ProviderType.GOOGLE_AI_GEMINI);
        google.getChat().setEnabled(true);
        google.getEmbedding().setEnabled(true);
        properties.getProviders().put("google-ai", google);

        Provider local = new Provider();
        local.setType(ProviderType.OPENAI);
        local.setBaseUrl("http://localhost:8000");
        local.getChat().setEnabled(true);
        properties.getProviders().put("local-gemma", local);

        Provider tei = new Provider();
        tei.setType(ProviderType.TEI);
        tei.setBaseUrl("http://localhost:18080");
        tei.getEmbedding().setEnabled(true);
        properties.getProviders().put("kure", tei);

        ModelDeploymentProperties deployments = new ModelDeploymentProperties();
        deployments.getModelDeployments().put(
                "chat-default", new ModelDeploymentProperties.Deployment());
        deployments.getModelDeployments().put(
                "humanities-text-v1", new ModelDeploymentProperties.Deployment());
        deployments.getRouting().setDefaultChatDeployment("chat-default");
        deployments.getRouting().setDefaultEmbeddingDeployment("humanities-text-v1");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.google.genai.chat.api-key", "test-key")
                .withProperty("spring.ai.google.genai.embedding.api-key", "test-key");

        assertDoesNotThrow(() -> guard(properties, deployments, environment).validate());
    }

    @Test
    void deploymentModeRequiresDeploymentRoutingDefaults() {
        AiAdapterProperties properties = new AiAdapterProperties();
        ModelDeploymentProperties deployments = new ModelDeploymentProperties();
        deployments.getModelDeployments().put(
                "chat-default", new ModelDeploymentProperties.Deployment());

        assertThrows(IllegalStateException.class,
                () -> guard(properties, deployments, environment()).validate());
    }

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
    void validateRejectsChatDefaultWithoutEmbeddingDefault() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.getRouting().setDefaultChatProvider("google-chat");

        AiSecretPresenceGuard guard = guard(properties, environment());

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateRejectsEmbeddingDefaultWithoutChatDefault() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.getRouting().setDefaultEmbeddingProvider("google-embedding");

        AiSecretPresenceGuard guard = guard(properties, environment());

        assertThrows(IllegalStateException.class, guard::validate);
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
    void validateAllowsConfiguredTeiEmbeddingProvider() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.setDefaultChatProvider("google");
        properties.setDefaultEmbeddingProvider("kure");

        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.TEI);
        provider.setBaseUrl("http://localhost:18080");
        provider.getEmbedding().setEnabled(true);
        provider.getEmbedding().setModel("nlpai-lab/KURE-v1");
        properties.getProviders().put("kure", provider);

        AiSecretPresenceGuard guard = guard(properties, environment());

        assertDoesNotThrow(guard::validate);
    }

    @Test
    void validateRejectsMissingBaseUrlForTeiEmbeddingProvider() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.setDefaultChatProvider("google");
        properties.setDefaultEmbeddingProvider("kure");

        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.TEI);
        provider.getEmbedding().setEnabled(true);
        provider.getEmbedding().setModel("nlpai-lab/KURE-v1");
        properties.getProviders().put("kure", provider);

        AiSecretPresenceGuard guard = guard(properties, environment());

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateRejectsMissingModelForTeiEmbeddingProvider() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setEnabled(true);
        properties.setDefaultChatProvider("google");
        properties.setDefaultEmbeddingProvider("kure");

        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.TEI);
        provider.setBaseUrl("http://localhost:18080");
        provider.getEmbedding().setEnabled(true);
        properties.getProviders().put("kure", provider);

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

    @Test
    void validateRejectsMultipleGoogleChatProvidersWhenUsingSingleSpringAiChatModel() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.getRouting().setDefaultChatProvider("google-primary");
        properties.getRouting().setDefaultEmbeddingProvider("google-primary");
        properties.getProviders().put("google-primary", googleProvider(true, false));
        properties.getProviders().put("google-backup", googleProvider(true, false));

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("googleChatModel", org.mockito.Mockito.mock(ChatModel.class));
        AiSecretPresenceGuard guard = guard(properties, environment(), beanFactory);

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateRejectsMultipleGoogleEmbeddingProvidersWhenUsingSingleSpringAiEmbeddingModel() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.getRouting().setDefaultChatProvider("google-primary");
        properties.getRouting().setDefaultEmbeddingProvider("google-primary");
        properties.getProviders().put("google-primary", googleProvider(false, true));
        properties.getProviders().put("google-backup", googleProvider(false, true));

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("googleEmbeddingModel", org.mockito.Mockito.mock(EmbeddingModel.class));
        AiSecretPresenceGuard guard = guard(properties, environment(), beanFactory);

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateAllowsGoogleProvidersWithExplicitModelOverrides() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.getRouting().setDefaultChatProvider("google-primary");
        properties.getRouting().setDefaultEmbeddingProvider("google-primary");
        Provider primary = googleProvider(true, true);
        Provider pro = googleProvider(true, false);
        pro.getChat().setModelOverride(true);
        pro.getChat().setModel("gemini-2.5-pro");
        Provider embedding2 = googleProvider(false, true);
        embedding2.getEmbedding().setModelOverride(true);
        embedding2.getEmbedding().setModel("gemini-embedding-2");
        properties.getProviders().put("google-primary", primary);
        properties.getProviders().put("google-pro", pro);
        properties.getProviders().put("google-embedding-2", embedding2);

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.google.genai.chat.api-key", "test-key")
                .withProperty("spring.ai.google.genai.chat.options.model", "gemini-2.5-flash")
                .withProperty("spring.ai.google.genai.embedding.api-key", "test-key")
                .withProperty("spring.ai.google.genai.embedding.text.options.model", "gemini-embedding-001");
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("googleChatModel", org.mockito.Mockito.mock(ChatModel.class));
        beanFactory.addBean("googleEmbeddingModel", org.mockito.Mockito.mock(EmbeddingModel.class));

        assertDoesNotThrow(() -> guard(properties, environment, beanFactory).validate());
    }

    @Test
    void validateRejectsBlankExplicitGoogleModelOverride() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("google");
        Provider provider = googleProvider(true, false);
        provider.getChat().setModelOverride(true);
        properties.getProviders().put("google", provider);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.google.genai.chat.api-key", "test-key")
                .withProperty("spring.ai.google.genai.chat.options.model", "gemini-2.5-flash");

        assertThrows(IllegalStateException.class, () -> guard(properties, environment).validate());
    }

    @Test
    void validateRejectsMultipleOllamaEmbeddingProvidersWhenUsingSingleSpringAiEmbeddingModel() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.getRouting().setDefaultChatProvider("ollama-primary");
        properties.getRouting().setDefaultEmbeddingProvider("ollama-primary");
        properties.getProviders().put("ollama-primary", ollamaProvider());
        properties.getProviders().put("ollama-backup", ollamaProvider());

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("ollamaEmbeddingModel", org.mockito.Mockito.mock(EmbeddingModel.class));
        AiSecretPresenceGuard guard = guard(properties, environment(), beanFactory);

        assertThrows(IllegalStateException.class, guard::validate);
    }

    private static Environment environment() {
        return new MockEnvironment();
    }

    private static AiSecretPresenceGuard guard(AiAdapterProperties properties, Environment environment) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        return guard(properties, new ModelDeploymentProperties(), environment, beanFactory);
    }

    private static AiSecretPresenceGuard guard(
            AiAdapterProperties properties,
            ModelDeploymentProperties deployments,
            Environment environment) {
        return guard(properties, deployments, environment, new StaticListableBeanFactory());
    }

    private static AiSecretPresenceGuard guard(
            AiAdapterProperties properties,
            Environment environment,
            StaticListableBeanFactory beanFactory) {
        return guard(properties, new ModelDeploymentProperties(), environment, beanFactory);
    }

    private static AiSecretPresenceGuard guard(
            AiAdapterProperties properties,
            ModelDeploymentProperties deployments,
            Environment environment,
            StaticListableBeanFactory beanFactory) {
        return new AiSecretPresenceGuard(
                properties,
                deployments,
                environment,
                beanFactory.getBeanProvider(ChatModel.class),
                beanFactory.getBeanProvider(EmbeddingModel.class));
    }

    private static Provider googleProvider(boolean chat, boolean embedding) {
        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.GOOGLE_AI_GEMINI);
        provider.getChat().setEnabled(chat);
        provider.getEmbedding().setEnabled(embedding);
        return provider;
    }

    private static Provider ollamaProvider() {
        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.OLLAMA);
        provider.getEmbedding().setEnabled(true);
        return provider;
    }
}
