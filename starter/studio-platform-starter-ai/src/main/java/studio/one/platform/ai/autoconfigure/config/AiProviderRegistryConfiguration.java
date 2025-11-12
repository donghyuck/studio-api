package studio.one.platform.ai.autoconfigure.config;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

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

        return new AiProviderRegistry(properties.getDefaultProvider(), chatPorts, embeddingPorts);
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
}