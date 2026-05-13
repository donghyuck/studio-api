package studio.one.platform.ai.autoconfigure.config;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;
import studio.one.platform.ai.autoconfigure.adapter.LangChainChatAdapter;
import studio.one.platform.ai.autoconfigure.adapter.LangChainEmbeddingAdapter;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "dev.langchain4j.model.openai.OpenAiChatModel")
public class OpenAiPortFactoryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenAiPortFactoryConfiguration.class);

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
        public ChatPort create(String providerId, AiAdapterProperties.Provider provider, Environment env) {
            String apiKey = requireText(value(env, providerId, "api-key", provider.getApiKey(), "spring.ai.openai.api-key"),
                    "studio.ai.providers." + providerId + ".api-key must be configured for OPENAI chat provider");
            String model = requireText(value(env, providerId, "chat.model", provider.getChat().getModel(),
                    "spring.ai.openai.chat.options.model"),
                    "studio.ai.providers." + providerId + ".chat.model must be configured for OPENAI chat provider");
            String baseUrl = value(env, providerId, "base-url", provider.getBaseUrl(), "spring.ai.openai.base-url");
            if (!StringUtils.isNotBlank(baseUrl)) {
                baseUrl = "https://api.openai.com/v1";
            }
            String resolvedBaseUrl = baseUrl;
            return new LangChainChatAdapter(
                    request -> openAiChatModel(apiKey, resolvedBaseUrl, model, request),
                    request -> openAiStreamingChatModel(apiKey, resolvedBaseUrl, model, request),
                    provider.getType().name(),
                    model);
        }

        private OpenAiChatModel openAiChatModel(String apiKey, String baseUrl, String model, ChatRequest request) {
            rejectUnsupportedTopK(request);
            OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName(model, request));
            applyOptions(builder, request);
            return builder.build();
        }

        private OpenAiStreamingChatModel openAiStreamingChatModel(
                String apiKey,
                String baseUrl,
                String model,
                ChatRequest request) {
            rejectUnsupportedTopK(request);
            OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName(model, request));
            applyOptions(builder, request);
            return builder.build();
        }

        private void applyOptions(OpenAiChatModel.OpenAiChatModelBuilder builder, ChatRequest request) {
            if (request.temperature() != null) {
                builder.temperature(request.temperature());
            }
            if (request.topP() != null) {
                builder.topP(request.topP());
            }
            if (request.maxOutputTokens() != null) {
                builder.maxTokens(request.maxOutputTokens());
            }
            if (!request.stopSequences().isEmpty()) {
                builder.stop(request.stopSequences());
            }
        }

        private void applyOptions(OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder, ChatRequest request) {
            if (request.temperature() != null) {
                builder.temperature(request.temperature());
            }
            if (request.topP() != null) {
                builder.topP(request.topP());
            }
            if (request.maxOutputTokens() != null) {
                builder.maxTokens(request.maxOutputTokens());
            }
            if (!request.stopSequences().isEmpty()) {
                builder.stop(request.stopSequences());
            }
        }

        private void rejectUnsupportedTopK(ChatRequest request) {
            if (request.topK() != null) {
                throw new IllegalArgumentException("OPENAI chat provider does not support request topK override");
            }
        }
    }

    static final class OpenAiEmbeddingPortFactory implements ProviderEmbeddingPortFactory {

        @Override
        public AiAdapterProperties.ProviderType supportedType() {
            return AiAdapterProperties.ProviderType.OPENAI;
        }

        @Override
        public EmbeddingPort create(String providerId, AiAdapterProperties.Provider provider, Environment env) {
            String apiKey = requireText(value(env, providerId, "api-key", provider.getApiKey(), "spring.ai.openai.api-key"),
                    "studio.ai.providers." + providerId + ".api-key must be configured for OPENAI embedding provider");
            String model = requireText(value(env, providerId, "embedding.model", provider.getEmbedding().getModel(),
                    "spring.ai.openai.embedding.options.model"),
                    "studio.ai.providers." + providerId + ".embedding.model must be configured for OPENAI embedding provider");
            String baseUrl = value(env, providerId, "base-url", provider.getBaseUrl(), "spring.ai.openai.base-url");
            if (!StringUtils.isNotBlank(baseUrl)) {
                baseUrl = "https://api.openai.com/v1";
            }
            Integer dimensions = integerValue(env, providerId, "embedding.dimension", provider.getEmbedding().getDimension(),
                    "spring.ai.openai.embedding.options.dimensions");
            OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(model);
            if (dimensions != null) {
                builder.dimensions(dimensions);
            }
            OpenAiEmbeddingModel embeddingModel = builder.build();
            return new LangChainEmbeddingAdapter(embeddingModel, model);
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

    private static Integer integerValue(
            Environment env,
            String providerId,
            String studioLeaf,
            Integer studioValue,
            String springKey) {
        String studioKey = "studio.ai.providers." + providerId + "." + studioLeaf;
        if (studioValue != null) {
            return studioValue;
        }
        Integer studioConfigured = env.getProperty(studioKey, Integer.class);
        if (studioConfigured != null) {
            return studioConfigured;
        }
        Integer legacyConfigured = env.getProperty(springKey, Integer.class);
        if (legacyConfigured != null) {
            ConfigurationPropertyMigration.warnDeprecated(log, springKey, studioKey,
                    "Use studio.ai.* as the canonical AI configuration namespace.");
        }
        return legacyConfigured;
    }
}
