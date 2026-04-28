package studio.one.platform.ai.autoconfigure.config;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Builds the {@code GoogleGenAiChatModel} directly using {@code spring.ai.google.genai.*}
 * provider options.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.ai.google.genai.GoogleGenAiChatModel")
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
        public ChatPort create(AiAdapterProperties.Provider provider,
                               Environment env,
                               ObjectProvider<org.springframework.ai.chat.model.ChatModel> chatModelProvider) {
            return create("<id>", provider, env, chatModelProvider);
        }

        @Override
        public ChatPort create(String providerId,
                               AiAdapterProperties.Provider provider,
                               Environment env,
                               ObjectProvider<org.springframework.ai.chat.model.ChatModel> chatModelProvider) {
            String model = AiConfigurationMigration.springOrLegacyProviderValue(
                    env,
                    "spring.ai.google.genai.chat.options.model",
                    "studio.ai.providers." + providerId + ".chat.model",
                    provider.getChat().getModel(),
                    log);
            String apiKey = requireText(
                    AiConfigurationMigration.springOrLegacyProviderValue(
                            env,
                            "spring.ai.google.genai.chat.api-key",
                            "studio.ai.providers." + providerId + ".api-key",
                            provider.getApiKey(),
                            log),
                    "spring.ai.google.genai.chat.api-key must be configured for GOOGLE_AI_GEMINI chat provider");
            model = requireText(model,
                    "spring.ai.google.genai.chat.options.model must be configured for GOOGLE_AI_GEMINI chat provider");
            String baseUrl = AiConfigurationMigration.springOrLegacyProviderValue(
                    env,
                    "spring.ai.google.genai.chat.base-url",
                    "studio.ai.providers." + providerId + ".base-url",
                    provider.getBaseUrl(),
                    log);

            com.google.genai.Client.Builder clientBuilder = com.google.genai.Client.builder().apiKey(apiKey);
            if (StringUtils.isNotBlank(baseUrl)) {
                clientBuilder.httpOptions(com.google.genai.types.HttpOptions.builder()
                        .baseUrl(baseUrl)
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
            return new GoogleSpringAiChatAdapter(chatModel, provider.getType().name(), model);
        }

        private static String requireText(String value, String message) {
            if (!StringUtils.isNotBlank(value)) {
                throw new IllegalStateException(message);
            }
            return value;
        }

    }
}
