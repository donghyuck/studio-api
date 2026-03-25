package studio.one.platform.ai.autoconfigure.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.autoconfigure.adapter.SpringAiChatAdapter;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiAdapterProperties.class)
@Slf4j
public class ProviderChatConfiguration {

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

    private static ChatPort createGoogleSpringAiChatPort(AiAdapterProperties.Provider provider, String baseUrl) {
        GoogleGenAiChatOptions.Builder optionsBuilder = GoogleGenAiChatOptions.builder()
                .model(requireModel(provider.getChat().getModel()));
        Client.Builder clientBuilder = Client.builder()
                .apiKey(provider.getApiKey());
        if (StringUtils.isNotBlank(baseUrl)) {
            clientBuilder.httpOptions(HttpOptions.builder()
                    .baseUrl(baseUrl)
                    .build());
        }
        return new SpringAiChatAdapter(GoogleGenAiChatModel.builder()
                .genAiClient(clientBuilder.build())
                .defaultOptions(optionsBuilder.build())
                .build());
    }

    private static ChatPort createChatPort(String providerName, AiAdapterProperties.Provider provider,
            Environment environment, I18n i18n,
            ObjectProvider<org.springframework.ai.chat.model.ChatModel> springAiChatModelProvider) {
        log.debug("Creating Chat Port by  {}", provider );
        ChatPort chatPort = switch (provider.getType()) {
            case OPENAI -> createSpringAiChatPort(springAiChatModelProvider);
            case GOOGLE_AI_GEMINI -> createGoogleSpringAiChatPort(provider, resolveBaseUrl(provider, environment));
            default -> throw new IllegalArgumentException("Unsupported chat provider type: " + provider.getType());
        };
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(ChatPort.class, true),
                LogUtils.green(chatPort.getClass(), true),
                LogUtils.red(State.CREATED.toString())));
        return chatPort;
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
            case GOOGLE_AI_GEMINI -> "https://generativelanguage.googleapis.com/v1beta";
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
