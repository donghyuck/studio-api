package studio.one.platform.ai.autoconfigure.config;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;
import studio.one.platform.ai.autoconfigure.adapter.LangChainEmbeddingAdapter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "dev.langchain4j.model.googleai.GoogleAiEmbeddingModel")
public class GoogleGenAiEmbeddingPortFactoryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiEmbeddingPortFactoryConfiguration.class);

    @Bean
    public ProviderEmbeddingPortFactory googleGenAiEmbeddingPortFactory() {
        return new GoogleGenAiEmbeddingPortFactory();
    }

    static final class GoogleGenAiEmbeddingPortFactory implements ProviderEmbeddingPortFactory {

        @Override
        public AiAdapterProperties.ProviderType supportedType() {
            return AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI;
        }

        @Override
        public EmbeddingPort create(String providerId, AiAdapterProperties.Provider provider, Environment env) {
            String model = requireText(value(env, providerId, "embedding.model", provider.getEmbedding().getModel(),
                    "spring.ai.google.genai.embedding.text.options.model"),
                    "studio.ai.providers." + providerId + ".embedding.model must be configured for GOOGLE_AI_GEMINI embedding provider");
            String apiKey = requireText(value(env, providerId, "api-key", provider.getApiKey(),
                    "spring.ai.google.genai.embedding.api-key"),
                    "studio.ai.providers." + providerId + ".api-key must be configured for GOOGLE_AI_GEMINI embedding provider");
            String baseUrl = value(env, providerId, "base-url", provider.getBaseUrl(),
                    "spring.ai.google.genai.embedding.text.base-url");
            if (StringUtils.isNotBlank(baseUrl)) {
                throw new IllegalStateException(
                        "GOOGLE_AI_GEMINI embedding base-url override is not supported by LangChain4j 0.35.0 on Java 11");
            }
            String taskType = value(env, providerId, "google-embedding.task-type",
                    provider.getGoogleEmbedding().getTaskType(),
                    "spring.ai.google.genai.embedding.text.options.task-type");
            Integer dimension = integerValue(env, providerId, "embedding.dimension",
                    provider.getEmbedding().getDimension(),
                    "spring.ai.google.genai.embedding.text.options.output-dimensionality",
                    "spring.ai.google.genai.embedding.text.options.dimensions");

            GoogleAiEmbeddingModel.GoogleAiEmbeddingModelBuilder builder = GoogleAiEmbeddingModel.builder()
                    .apiKey(apiKey)
                    .modelName(model);
            if (StringUtils.isNotBlank(taskType)) {
                builder.taskType(GoogleAiEmbeddingModel.TaskType.valueOf(taskType.trim().toUpperCase(Locale.ROOT)));
            }
            if (dimension != null) {
                builder.outputDimensionality(dimension);
            }
            return new LangChainEmbeddingAdapter(builder.build(), model);
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

    private static Integer integerValue(
            Environment env,
            String providerId,
            String studioLeaf,
            Integer studioValue,
            String springKey,
            String... additionalSpringKeys) {
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
            return legacyConfigured;
        }
        for (String additionalSpringKey : additionalSpringKeys) {
            legacyConfigured = env.getProperty(additionalSpringKey, Integer.class);
            if (legacyConfigured != null) {
                ConfigurationPropertyMigration.warnDeprecated(log, additionalSpringKey, studioKey,
                        "Use studio.ai.* as the canonical AI configuration namespace.");
                return legacyConfigured;
            }
        }
        return null;
    }
}
