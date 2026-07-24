package studio.one.platform.ai.autoconfigure.config;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.model.ModelDeployment;
import studio.one.platform.ai.model.ModelDeploymentRegistry;
import studio.one.platform.ai.model.ModelWorkload;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiAdapterProperties.class)
@RequiredArgsConstructor
@Slf4j
public class AiProviderRegistryConfiguration {

    protected static final String FEATURE_NAME = "AI";
    private final ObjectProvider<I18n> i18nProvider;

    @Bean
    public AiProviderRegistry aiProviderRegistry(AiAdapterProperties properties,
            Environment environment,
            @Qualifier("providerChatPorts") Map<String, ChatPort> chatPorts,
            @Qualifier("providerEmbeddingPorts") Map<String, EmbeddingPort> embeddingPorts,
            ObjectProvider<ModelDeploymentProperties> deploymentPropertiesProvider,
            @Qualifier("modelDeploymentRegistry") ObjectProvider<ModelDeploymentRegistry> deploymentRegistryProvider) {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(AiProviderRegistry.class, true), LogUtils.red(State.CREATED.toString())));

        ModelDeploymentProperties deploymentProperties = deploymentPropertiesProvider.getIfAvailable();
        ModelDeploymentRegistry deploymentRegistry = deploymentRegistryProvider.getIfAvailable();
        if (deploymentProperties != null && deploymentRegistry != null
                && !deploymentProperties.getModelDeployments().isEmpty()) {
            return deploymentBackedProviderRegistry(deploymentRegistry);
        }
        return legacyProviderRegistry(properties, environment, chatPorts, embeddingPorts);
    }

    private AiProviderRegistry legacyProviderRegistry(
            AiAdapterProperties properties,
            Environment environment,
            Map<String, ChatPort> chatPorts,
            Map<String, EmbeddingPort> embeddingPorts) {
        AiConfigurationMigration.RoutingDefaults routing =
                AiConfigurationMigration.resolveRouting(properties, environment, log);
        if (routing.defaultChatProvider() == null || routing.defaultEmbeddingProvider() == null) {
            throw new IllegalStateException("studio.ai.routing.default-chat-provider and " +
                    "studio.ai.routing.default-embedding-provider must be configured unless legacy " +
                    "studio.ai.default-provider is configured");
        }
        requirePort(chatPorts, routing.defaultChatProvider(),
                "studio.ai.routing.default-chat-provider", ChatPort.class);
        requirePort(embeddingPorts, routing.defaultEmbeddingProvider(),
                "studio.ai.routing.default-embedding-provider", EmbeddingPort.class);
        return new AiProviderRegistry(
                routing.defaultProvider(),
                routing.defaultChatProvider(),
                routing.defaultEmbeddingProvider(),
                chatPorts,
                embeddingPorts);
    }

    private AiProviderRegistry deploymentBackedProviderRegistry(ModelDeploymentRegistry registry) {
        Map<String, ChatPort> chatPorts = new java.util.LinkedHashMap<>();
        Map<String, EmbeddingPort> embeddingPorts = new java.util.LinkedHashMap<>();
        ModelDeployment defaultChat = registry.defaultDeployment(ModelWorkload.CHAT).orElse(null);
        ModelDeployment defaultEmbedding = registry.defaultDeployment(ModelWorkload.EMBEDDING).orElse(null);
        addChatPort(chatPorts, registry, defaultChat);
        addEmbeddingPort(embeddingPorts, registry, defaultEmbedding);
        registry.deployments(ModelWorkload.CHAT)
                .forEach(deployment -> addChatPort(chatPorts, registry, deployment));
        registry.deployments(ModelWorkload.EMBEDDING)
                .forEach(deployment -> addEmbeddingPort(embeddingPorts, registry, deployment));
        String defaultChatProvider = defaultChat == null ? null : defaultChat.providerRef();
        String defaultEmbeddingProvider = defaultEmbedding == null ? null : defaultEmbedding.providerRef();
        return new AiProviderRegistry(
                defaultChatProvider, defaultChatProvider, defaultEmbeddingProvider, chatPorts, embeddingPorts);
    }

    private void addChatPort(Map<String, ChatPort> ports, ModelDeploymentRegistry registry,
            ModelDeployment deployment) {
        if (deployment != null) {
            ports.putIfAbsent(deployment.providerRef(), registry.chatPort(deployment.deploymentId()));
        }
    }

    private void addEmbeddingPort(Map<String, EmbeddingPort> ports, ModelDeploymentRegistry registry,
            ModelDeployment deployment) {
        if (deployment != null) {
            ports.putIfAbsent(deployment.providerRef(), registry.embeddingPort(deployment.deploymentId()));
        }
    }

    AiProviderRegistry aiProviderRegistry(AiAdapterProperties properties,
            Map<String, ChatPort> chatPorts,
            Map<String, EmbeddingPort> embeddingPorts) {
        return legacyProviderRegistry(properties, null, chatPorts, embeddingPorts);
    }

    @Bean
    public ChatPort defaultChatPort(AiProviderRegistry registry) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(AiProviderRegistry.class, true),
                LogUtils.green(ChatPort.class, true),
                LogUtils.red(State.CREATED.toString())));

        return registry.chatPort(null);
    }

    @Bean
    public EmbeddingPort defaultEmbeddingPort(AiProviderRegistry registry) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(AiProviderRegistry.class, true),
                LogUtils.green(EmbeddingPort.class, true),
                LogUtils.red(State.CREATED.toString())));

        return request -> registry.embeddingPort(request.provider()).embed(request);
    }

    private static <T> void requirePort(Map<String, T> ports, String provider, String propertyName, Class<?> portType) {
        if (provider == null) {
            throw new IllegalStateException(propertyName + " must be configured");
        }
        if (ports.keySet().stream().map(AiProviderRegistryConfiguration::normalize).noneMatch(provider::equals)) {
            throw new IllegalStateException(propertyName + " '" + provider + "' has no registered " +
                    portType.getSimpleName() + ". Ensure the provider library is on the classpath and the provider " +
                    "channel is enabled in studio.ai.providers.");
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.toLowerCase(java.util.Locale.ROOT);
    }
}
