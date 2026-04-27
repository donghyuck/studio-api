package studio.one.platform.ai.autoconfigure.config;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
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
            @Qualifier("providerChatPorts") Map<String, ChatPort> chatPorts,
            @Qualifier("providerEmbeddingPorts") Map<String, EmbeddingPort> embeddingPorts) {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(AiProviderRegistry.class, true), LogUtils.red(State.CREATED.toString())));

        String legacyDefaultProvider = normalize(properties.getDefaultProvider());
        String defaultChatProvider = firstNonBlank(properties.getDefaultChatProvider(), legacyDefaultProvider);
        String defaultEmbeddingProvider = firstNonBlank(properties.getDefaultEmbeddingProvider(), legacyDefaultProvider);
        if (legacyDefaultProvider == null && (defaultChatProvider == null || defaultEmbeddingProvider == null)) {
            throw new IllegalStateException("studio.ai.default-provider must be configured unless both " +
                    "studio.ai.default-chat-provider and studio.ai.default-embedding-provider are configured");
        }
        if (legacyDefaultProvider != null) {
            requirePort(chatPorts, legacyDefaultProvider, "studio.ai.default-provider", ChatPort.class);
            requirePort(embeddingPorts, legacyDefaultProvider, "studio.ai.default-provider", EmbeddingPort.class);
        }
        requirePort(chatPorts, defaultChatProvider, "studio.ai.default-chat-provider", ChatPort.class);
        requirePort(embeddingPorts, defaultEmbeddingProvider, "studio.ai.default-embedding-provider", EmbeddingPort.class);
        String defaultProvider = legacyDefaultProvider == null ? defaultChatProvider : legacyDefaultProvider;
        return new AiProviderRegistry(defaultProvider, defaultChatProvider, defaultEmbeddingProvider, chatPorts, embeddingPorts);
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

        return registry.embeddingPort(null);
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

    private static String firstNonBlank(String primary, String fallback) {
        String normalized = normalize(primary);
        return normalized == null ? fallback : normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.toLowerCase(java.util.Locale.ROOT);
    }
}
