package studio.one.platform.ai.adapters.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import studio.one.platform.ai.adapters.chat.LangChainChatAdapter;
import studio.one.platform.ai.core.chat.ChatPort;

@Configuration
@EnableConfigurationProperties(AiAdapterProperties.class)
public class LangChainChatConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "openai", matchIfMissing = true)
    public ChatModel openAiChatModel(AiAdapterProperties properties) {
        AiAdapterProperties.OpenAiProperties openai = properties.getOpenai();
        return OpenAiChatModel.builder()
                .apiKey(openai.getApiKey())
                .baseUrl(openai.getBaseUrl())
                .modelName(openai.getChatModel())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "google-ai-gemini")
    public ChatModel googleAiGeminiChatModel(AiAdapterProperties properties) {
        AiAdapterProperties.GoogleAiGeminiProperties google = properties.getGoogleAiGemini();
        return GoogleAiGeminiChatModel.builder()
                .apiKey(google.getApiKey())
                .baseUrl(google.getBaseUrl())
                .modelName(google.getChatModel())
                .build();
    }

    @Bean
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnMissingBean(ChatPort.class)
    public ChatPort chatPort(ChatModel chatModel) {
        return new LangChainChatAdapter(chatModel);
    }
}
