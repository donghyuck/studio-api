package studio.one.platform.ai.autoconfigure.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.autoconfigure.adapter.SpringAiChatAdapter;
import studio.one.platform.ai.adapters.chat.LangChainChatAdapter;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiAdapterProperties.class)
@Slf4j
public class LangChainChatConfiguration {

    @Bean(name = "providerChatPorts")
    public Map<String, ChatPort> chatPorts(AiAdapterProperties properties,
            ObjectProvider<I18n> i18nProvider,
            Environment environment,
            ObjectProvider<org.springframework.ai.chat.model.ChatModel> springAiChatModelProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        Map<String, ChatPort> ports = new LinkedHashMap<>();
        for (Map.Entry<String, AiAdapterProperties.Provider> entry : properties.getProviders().entrySet()) {
            AiAdapterProperties.Provider provider = entry.getValue();
            log.debug("checking <{}> : provider - {}, chat - {}", entry.getKey(), provider.isEnabled(), provider.getChat().isEnabled());
           
            if (!provider.isEnabled() || !provider.getChat().isEnabled()) {
                continue;
            }
            ports.put(entry.getKey(), createChatPort(entry.getKey(), provider, environment, i18n, springAiChatModelProvider));
        }
        return ports;
    }

    private static ChatPort createSpringAiChatPort(
            ObjectProvider<org.springframework.ai.chat.model.ChatModel> springAiChatModelProvider) {
        org.springframework.ai.chat.model.ChatModel chatModel = springAiChatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new IllegalStateException("Spring AI chat model bean is required for OPENAI provider");
        }
        return new SpringAiChatAdapter(chatModel);
    }

    private static ChatPort createChatPort(String providerName, AiAdapterProperties.Provider provider,
            Environment environment, I18n i18n,
            ObjectProvider<org.springframework.ai.chat.model.ChatModel> springAiChatModelProvider) {
        log.debug("Creating Chat Port by  {}", provider );
        ChatModel chatModel = switch (provider.getType()) {
            case OPENAI -> null;
            case GOOGLE_AI_GEMINI -> buildGoogleChat(provider, i18n, resolveBaseUrl(provider, environment), requireModel(provider.getChat().getModel()));
            default -> throw new IllegalArgumentException("Unsupported chat provider type: " + provider.getType());
        };
        if (provider.getType() == AiAdapterProperties.ProviderType.OPENAI) {
            return createSpringAiChatPort(springAiChatModelProvider);
        }
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(ChatModel.class, true),
                LogUtils.green(chatModel.getClass(), true),
                LogUtils.red(State.CREATED.toString())));
        return new LangChainChatAdapter(chatModel);
    }

    private static ChatModel buildGoogleChat(AiAdapterProperties.Provider provider, I18n i18n, String baseUrl, String model) {
        GoogleAiGeminiChatModelBuilder builder = GoogleAiGeminiChatModel.builder()
                .apiKey(provider.getApiKey())
                .baseUrl(baseUrl)
                .modelName(model);
        if (StringUtils.isNoneBlank(provider.getBaseUrl())) {
            builder.baseUrl(provider.getBaseUrl());
        }
        return builder.build();
    }

    private static String resolveBaseUrl(AiAdapterProperties.Provider provider, Environment environment) {
        if (provider.getType() == AiAdapterProperties.ProviderType.OPENAI) {
            String configured = environment.getProperty("spring.ai.openai.base-url");
            if (StringUtils.isNotBlank(configured)) {
                return configured;
            }
        }
        if (StringUtils.isNotBlank(provider.getBaseUrl())) {
            return provider.getBaseUrl();
        } 
        return switch (provider.getType()) {
            case OPENAI -> "https://api.openai.com/v1";
            //case GOOGLE_AI_GEMINI -> "https://generativelanguage.googleapis.com/v1";
            default -> null;
        };
    }

    private static String requireModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model name must be provided for provider chat configuration");
        }
        return model;
    }
}
