package studio.one.platform.ai.autoconfigure.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import studio.one.platform.ai.autoconfigure.adapter.GoogleSpringAiChatAdapter;
import studio.one.platform.ai.core.chat.ChatPort;

/**
 * Registers a {@link ProviderChatPortFactory} bean for the Google GenAI provider.
 * Active only when {@code spring-ai-google-genai} is on the classpath.
 * Builds the {@code GoogleGenAiChatModel} directly using {@code studio.ai.*} configuration.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.ai.google.genai.GoogleGenAiChatModel")
public class GoogleGenAiChatPortFactoryConfiguration {

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
        public ChatPort create(AiAdapterProperties.Provider provider,
                               Environment env,
                               ObjectProvider<org.springframework.ai.chat.model.ChatModel> chatModelProvider) {
            String apiKey = requireText(provider.getApiKey(),
                    "studio.ai.providers.<id>.api-key must be configured for GOOGLE_AI_GEMINI chat provider");
            String model = requireText(provider.getChat().getModel(),
                    "studio.ai.providers.<id>.chat.model must be configured for GOOGLE_AI_GEMINI chat provider");

            com.google.genai.Client.Builder clientBuilder = com.google.genai.Client.builder().apiKey(apiKey);
            if (StringUtils.isNotBlank(provider.getBaseUrl())) {
                clientBuilder.httpOptions(com.google.genai.types.HttpOptions.builder()
                        .baseUrl(provider.getBaseUrl())
                        .build());
            }
            com.google.genai.Client client = clientBuilder.build();

            org.springframework.ai.google.genai.GoogleGenAiChatOptions defaultOptions =
                    org.springframework.ai.google.genai.GoogleGenAiChatOptions.builder()
                            .model(model)
                            .build();
            org.springframework.ai.google.genai.GoogleGenAiChatModel chatModel =
                    org.springframework.ai.google.genai.GoogleGenAiChatModel.builder()
                            .genAiClient(client)
                            .defaultOptions(defaultOptions)
                            .build();
            return new GoogleSpringAiChatAdapter(chatModel);
        }

        private static String requireText(String value, String message) {
            if (!StringUtils.isNotBlank(value)) {
                throw new IllegalStateException(message);
            }
            return value;
        }
    }
}
