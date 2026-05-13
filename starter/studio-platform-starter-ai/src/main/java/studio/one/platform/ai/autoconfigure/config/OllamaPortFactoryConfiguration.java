package studio.one.platform.ai.autoconfigure.config;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import studio.one.platform.ai.autoconfigure.adapter.LangChainChatAdapter;
import studio.one.platform.ai.autoconfigure.adapter.LangChainEmbeddingAdapter;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "dev.langchain4j.model.ollama.OllamaEmbeddingModel")
public class OllamaPortFactoryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OllamaPortFactoryConfiguration.class);

    @Bean
    public ProviderChatPortFactory ollamaChatPortFactory() {
        return new OllamaChatPortFactory();
    }

    @Bean
    public ProviderEmbeddingPortFactory ollamaEmbeddingPortFactory() {
        return new OllamaEmbeddingPortFactory();
    }

    static final class OllamaChatPortFactory implements ProviderChatPortFactory {
        @Override
        public AiAdapterProperties.ProviderType supportedType() {
            return AiAdapterProperties.ProviderType.OLLAMA;
        }

        @Override
        public ChatPort create(String providerId, AiAdapterProperties.Provider provider, Environment env) {
            String model = requireText(value(env, providerId, "chat.model", provider.getChat().getModel(),
                    "spring.ai.ollama.chat.options.model"),
                    "studio.ai.providers." + providerId + ".chat.model must be configured for OLLAMA chat provider");
            String baseUrl = baseUrl(env, providerId, provider);
            return new LangChainChatAdapter(
                    request -> ollamaChatModel(baseUrl, model, request),
                    request -> ollamaStreamingChatModel(baseUrl, model, request),
                    provider.getType().name(),
                    model);
        }

        private OllamaChatModel ollamaChatModel(String baseUrl, String model, ChatRequest request) {
            OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName(model, request));
            applyOptions(builder, request);
            return builder.build();
        }

        private OllamaStreamingChatModel ollamaStreamingChatModel(String baseUrl, String model, ChatRequest request) {
            OllamaStreamingChatModel.OllamaStreamingChatModelBuilder builder = OllamaStreamingChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName(model, request));
            applyOptions(builder, request);
            return builder.build();
        }

        private void applyOptions(OllamaChatModel.OllamaChatModelBuilder builder, ChatRequest request) {
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
                builder.numPredict(request.maxOutputTokens());
            }
            if (!request.stopSequences().isEmpty()) {
                builder.stop(request.stopSequences());
            }
        }

        private void applyOptions(OllamaStreamingChatModel.OllamaStreamingChatModelBuilder builder, ChatRequest request) {
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
                builder.numPredict(request.maxOutputTokens());
            }
            if (!request.stopSequences().isEmpty()) {
                builder.stop(request.stopSequences());
            }
        }
    }

    static final class OllamaEmbeddingPortFactory implements ProviderEmbeddingPortFactory {
        @Override
        public AiAdapterProperties.ProviderType supportedType() {
            return AiAdapterProperties.ProviderType.OLLAMA;
        }

        @Override
        public EmbeddingPort create(String providerId, AiAdapterProperties.Provider provider, Environment env) {
            String model = requireText(value(env, providerId, "embedding.model", provider.getEmbedding().getModel(),
                    "spring.ai.ollama.embedding.options.model"),
                    "studio.ai.providers." + providerId + ".embedding.model must be configured for OLLAMA embedding provider");
            String baseUrl = baseUrl(env, providerId, provider);
            OllamaEmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(model)
                    .build();
            return new LangChainEmbeddingAdapter(embeddingModel, model);
        }
    }

    private static String baseUrl(Environment env, String providerId, AiAdapterProperties.Provider provider) {
        String baseUrl = value(env, providerId, "base-url", provider.getBaseUrl(), "spring.ai.ollama.base-url");
        return StringUtils.isNotBlank(baseUrl) ? baseUrl : "http://localhost:11434";
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
