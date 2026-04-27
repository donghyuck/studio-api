package studio.one.platform.ai.autoconfigure.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import studio.one.platform.ai.autoconfigure.adapter.SpringAiChatAdapter;
import studio.one.platform.ai.autoconfigure.adapter.SpringAiEmbeddingAdapter;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

/**
 * Registers {@link ProviderChatPortFactory} and {@link ProviderEmbeddingPortFactory} beans
 * for the OpenAI provider. Active only when {@code spring-ai-starter-model-openai} is on
 * the classpath. The factory delegates to the {@code ChatModel} / {@code EmbeddingModel}
 * beans created by Spring AI's own OpenAI auto-configuration.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.ai.openai.OpenAiChatModel")
public class OpenAiPortFactoryConfiguration {

    @Bean
    public ProviderChatPortFactory openAiChatPortFactory() {
        return new OpenAiChatPortFactory();
    }

    @Bean
    public ProviderEmbeddingPortFactory openAiEmbeddingPortFactory() {
        return new OpenAiEmbeddingPortFactory();
    }

    static final class OpenAiChatPortFactory implements ProviderChatPortFactory {

        @Override
        public AiAdapterProperties.ProviderType supportedType() {
            return AiAdapterProperties.ProviderType.OPENAI;
        }

        @Override
        public ChatPort create(AiAdapterProperties.Provider provider,
                               Environment env,
                               ObjectProvider<org.springframework.ai.chat.model.ChatModel> chatModelProvider) {
            org.springframework.ai.chat.model.ChatModel chatModel = chatModelProvider.getIfAvailable();
            if (chatModel == null) {
                throw new IllegalStateException(
                        "Spring AI ChatModel bean is required for OPENAI provider. " +
                        "Ensure spring-ai-starter-model-openai is on the classpath and spring.ai.openai.api-key is configured.");
            }
            return new SpringAiChatAdapter(chatModel, provider.getType().name(), provider.getChat().getModel());
        }
    }

    static final class OpenAiEmbeddingPortFactory implements ProviderEmbeddingPortFactory {

        @Override
        public AiAdapterProperties.ProviderType supportedType() {
            return AiAdapterProperties.ProviderType.OPENAI;
        }

        @Override
        public EmbeddingPort create(AiAdapterProperties.Provider provider,
                                    Environment env,
                                    ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModelProvider) {
            org.springframework.ai.embedding.EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
            if (embeddingModel == null) {
                throw new IllegalStateException(
                        "Spring AI EmbeddingModel bean is required for OPENAI provider. " +
                        "Ensure spring-ai-starter-model-openai is on the classpath and spring.ai.openai.api-key is configured.");
            }
            return new SpringAiEmbeddingAdapter(
                    embeddingModel,
                    env.getProperty("spring.ai.openai.embedding.options.model"));
        }
    }
}
