package studio.one.platform.ai.autoconfigure.config;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import studio.one.platform.ai.autoconfigure.adapter.LangChainChatAdapter;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "dev.langchain4j.model.googleai.GoogleAiGeminiChatModel")
public class GoogleGenAiChatPortFactoryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiChatPortFactoryConfiguration.class);

    @Bean
    public ProviderChatPortFactory googleGenAiChatPortFactory() {
        return new GoogleGenAiChatPortFactory();
    }

    static final class GoogleGenAiChatPortFactory implements ProviderChatPortFactory {

        @Override
        public AiAdapterProperties.ProviderType supportedType() {
            return AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI;
        }

        @Override
        public ChatPort create(String providerId, AiAdapterProperties.Provider provider, Environment env) {
            String model = requireText(value(env, providerId, "chat.model", provider.getChat().getModel(),
                    "spring.ai.google.genai.chat.options.model"),
                    "studio.ai.providers." + providerId + ".chat.model must be configured for GOOGLE_AI_GEMINI chat provider");
            String apiKey = requireText(value(env, providerId, "api-key", provider.getApiKey(),
                    "spring.ai.google.genai.chat.api-key"),
                    "studio.ai.providers." + providerId + ".api-key must be configured for GOOGLE_AI_GEMINI chat provider");
            String baseUrl = value(env, providerId, "base-url", provider.getBaseUrl(),
                    "spring.ai.google.genai.chat.base-url");
            if (StringUtils.isNotBlank(baseUrl)) {
                throw new IllegalStateException(
                        "GOOGLE_AI_GEMINI chat base-url override is not supported by LangChain4j 0.35.0 on Java 11");
            }
            return new LangChainChatAdapter(
                    request -> googleAiGeminiChatModel(apiKey, model, request),
                    null,
                    provider.getType().name(),
                    model,
                    true);
        }

        private GoogleAiGeminiChatModel googleAiGeminiChatModel(String apiKey, String model, ChatRequest request) {
            GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder = GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName(model, request));
            if (request.temperature() != null) {
                builder.temperature(request.temperature());
            }
            if (request.topK() != null) {
                builder.topK(request.topK());
            }
            if (request.topP() != null) {
                builder.topP(request.topP());
            }
            if (request.maxOutputTokens() != null) {
                builder.maxOutputTokens(request.maxOutputTokens());
            }
            if (!request.stopSequences().isEmpty()) {
                builder.stopSequences(request.stopSequences());
            }
            return builder.build();
        }
    }

    private static String value(Environment env, String providerId, String studioLeaf, String studioValue, String springKey) {
        return AiConfigurationMigration.studioOrSpringProviderValue(
                env,
                "studio.ai.providers." + providerId + "." + studioLeaf,
                studioValue,
                springKey,
                log);
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.isNotBlank(value)) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private static String modelName(String configuredModel, ChatRequest request) {
        return StringUtils.isNotBlank(request.model()) ? request.model().trim() : configuredModel;
    }
}
