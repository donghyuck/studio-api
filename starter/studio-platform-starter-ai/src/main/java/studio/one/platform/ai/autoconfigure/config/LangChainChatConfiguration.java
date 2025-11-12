package studio.one.platform.ai.autoconfigure.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import studio.one.platform.ai.adapters.chat.LangChainChatAdapter;
import studio.one.platform.ai.core.chat.ChatPort;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class LangChainChatConfiguration {

    @Bean
    public Map<String, ChatPort> chatPorts(AiAdapterProperties properties) {
        Map<String, ChatPort> ports = new LinkedHashMap<>();
        AiAdapterProperties.OpenAiProperties openai = properties.getOpenai();
        if (openai.isEnabled() && openai.getChat().isEnabled()) {
            ports.put("openai", createOpenAiChat(openai));
        }
        AiAdapterProperties.GoogleAiGeminiProperties google = properties.getGoogleAiGemini();
        if (google.isEnabled() && google.getChat().isEnabled()) {
            ports.put("google-ai-gemini", createGoogleAiChat(google));
        }
        return ports;
    }

    private static ChatPort createOpenAiChat(AiAdapterProperties.OpenAiProperties properties) {
        ChatModel model = OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(requireModel(properties.getChat().getOptions().getModel()))
                .build();
        return new LangChainChatAdapter(model);
    }

    private static ChatPort createGoogleAiChat(AiAdapterProperties.GoogleAiGeminiProperties properties) {
        ChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(requireModel(properties.getChat().getOptions().getModel()))
                .build();
        return new LangChainChatAdapter(model);
    }

    private static String requireModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model name must be provided");
        }
        return model;
    }
}
