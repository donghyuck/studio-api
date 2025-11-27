package studio.one.platform.ai.autoconfigure.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
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
    public Map<String, ChatPort> chatPorts(AiAdapterProperties properties, ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        Map<String, ChatPort> ports = new LinkedHashMap<>();
        for (Map.Entry<String, AiAdapterProperties.Provider> entry : properties.getProviders().entrySet()) {
            AiAdapterProperties.Provider provider = entry.getValue();
            log.debug("checking <{}> : provider - {}, chat - {}", entry.getKey(), provider.isEnabled(), provider.getChat().isEnabled());
           
            if (!provider.isEnabled() || !provider.getChat().isEnabled()) {
                continue;
            }
            ports.put(entry.getKey(), createChatPort(provider, i18n));
        }
        return ports;
    }

    private static ChatPort createChatPort(AiAdapterProperties.Provider provider, I18n i18n) {
        log.debug("Creating Chat Port by  {}", provider );
        String model = requireModel(provider.getChat().getModel());
        String baseUrl = resolveBaseUrl(provider);
        ChatModel chatModel = switch (provider.getType()) {
            case OPENAI -> OpenAiChatModel.builder()
                    .apiKey(provider.getApiKey())
                    .baseUrl(baseUrl)
                    .modelName(model)
                    .build();
            case GOOGLE_AI_GEMINI -> buildGoogleChat(provider, i18n, baseUrl, model);
            default -> throw new IllegalArgumentException("Unsupported chat provider type: " + provider.getType());
        };
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

    private static String resolveBaseUrl(AiAdapterProperties.Provider provider) {
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
