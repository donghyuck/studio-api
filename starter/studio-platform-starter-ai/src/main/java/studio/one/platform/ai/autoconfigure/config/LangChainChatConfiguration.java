package studio.one.platform.ai.autoconfigure.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.adapters.chat.LangChainChatAdapter;
import studio.one.platform.ai.adapters.vector.PgVectorStoreAdapter;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.vector.VectorStorePort;
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
        AiAdapterProperties.OpenAiProperties openai = properties.getOpenai();
        if (openai.isEnabled() && openai.getChat().isEnabled()) {
            ports.put("openai", createOpenAiChat(openai, i18n));
        }
        AiAdapterProperties.GoogleAiGeminiProperties google = properties.getGoogleAiGemini();
        if (google.isEnabled() && google.getChat().isEnabled()) {
            ports.put("google-ai-gemini", createGoogleAiChat(google, i18n));
        }
        return ports;
    }

    private static ChatPort createOpenAiChat(AiAdapterProperties.OpenAiProperties properties, I18n i18n) {
        ChatModel model = OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(requireModel(properties.getChat().getOptions().getModel()))
                .build();
          log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(ChatModel.class, true),
                LogUtils.green(OpenAiChatModel.class, true),
                LogUtils.red(State.CREATED.toString())));
        return new LangChainChatAdapter(model);
    }

    private static ChatPort createGoogleAiChat(AiAdapterProperties.GoogleAiGeminiProperties properties, I18n i18n) {
        ChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(requireModel(properties.getChat().getOptions().getModel()))
                .build();
          log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(ChatModel.class, true),
                LogUtils.green(GoogleAiGeminiChatModel.class, true),
                LogUtils.red(State.CREATED.toString())));     
        return new LangChainChatAdapter(model);
    }

    private static String requireModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model name must be provided");
        }
        return model;
    }
}
