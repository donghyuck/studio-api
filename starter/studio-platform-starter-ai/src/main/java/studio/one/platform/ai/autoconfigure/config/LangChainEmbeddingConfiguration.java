package studio.one.platform.ai.autoconfigure.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.autoconfigure.adapter.SpringAiEmbeddingAdapter;
import studio.one.platform.ai.adapters.embedding.LangChainEmbeddingAdapter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiAdapterProperties.class)
@Slf4j
public class LangChainEmbeddingConfiguration {

    @Bean(name = "providerEmbeddingPorts")
    public Map<String, EmbeddingPort> embeddingPorts(AiAdapterProperties properties,
                                                    ObjectProvider<I18n> i18nProvider,
                                                    Environment environment,
                                                    ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> springAiEmbeddingModelProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        Map<String, EmbeddingPort> ports = new LinkedHashMap<>();
        for (Map.Entry<String, AiAdapterProperties.Provider> entry : properties.getProviders().entrySet()) {
            AiAdapterProperties.Provider provider = entry.getValue();
            log.debug("checking <{}> : provider - {}, embedding - {}", entry.getKey(), provider.isEnabled(), provider.getEmbedding().isEnabled());
            if (!provider.isEnabled() || !provider.getEmbedding().isEnabled()) {
                continue;
            }
            ports.put(entry.getKey(), createEmbedding(provider, environment, i18n, springAiEmbeddingModelProvider));
        }
        return ports;
    }

    private static EmbeddingPort createSpringAiEmbeddingPort(
            ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> springAiEmbeddingModelProvider) {
        org.springframework.ai.embedding.EmbeddingModel embeddingModel = springAiEmbeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            throw new IllegalStateException("Spring AI embedding model bean is required for OPENAI provider");
        }
        return new SpringAiEmbeddingAdapter(embeddingModel);
    }

    private static EmbeddingPort createEmbedding(AiAdapterProperties.Provider provider,
            Environment environment, I18n i18n,
            ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> springAiEmbeddingModelProvider) {
        log.debug("Creating Embedding Port by  {}", provider );
        EmbeddingModel embeddingModel = switch (provider.getType()) {
            case OPENAI -> null;
            case OLLAMA -> null;
            case GOOGLE_AI_GEMINI -> buildGoogleEmbedding(provider, resolveBaseUrl(provider, environment), requireModel(provider.getEmbedding().getModel()));
            default -> throw new IllegalArgumentException("Unsupported embedding provider: " + provider.getType());
        };
        if (provider.getType() == AiAdapterProperties.ProviderType.OPENAI) {
            return createSpringAiEmbeddingPort(springAiEmbeddingModelProvider);
        }
        if (provider.getType() == AiAdapterProperties.ProviderType.OLLAMA) {
            return createOllamaSpringAiEmbeddingPort(environment);
        }
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(EmbeddingModel.class, true),
                LogUtils.green(embeddingModel.getClass(), true),
                LogUtils.red(State.CREATED.toString())));
        return new LangChainEmbeddingAdapter(embeddingModel);
    }

    private static EmbeddingPort createOllamaSpringAiEmbeddingPort(Environment environment) {
        String model = requireText(environment.getProperty("spring.ai.ollama.embedding.options.model"),
                "spring.ai.ollama.embedding.options.model must be configured for OLLAMA embedding provider");
        String baseUrl = environment.getProperty("spring.ai.ollama.base-url", "http://localhost:11434");
        org.springframework.ai.ollama.api.OllamaApi ollamaApi = org.springframework.ai.ollama.api.OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();
        org.springframework.ai.ollama.api.OllamaEmbeddingOptions options = org.springframework.ai.ollama.api.OllamaEmbeddingOptions.builder()
                .model(model)
                .build();
        org.springframework.ai.ollama.OllamaEmbeddingModel embeddingModel = org.springframework.ai.ollama.OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .build();
        return new SpringAiEmbeddingAdapter(embeddingModel);
    }

    private static GoogleAiEmbeddingModel buildGoogleEmbedding(AiAdapterProperties.Provider provider, String baseUrl, String model) {
        AiAdapterProperties.GoogleEmbeddingOptions google = provider.getGoogleEmbedding();
        GoogleAiEmbeddingModel.GoogleAiEmbeddingModelBuilder builder = GoogleAiEmbeddingModel.builder()
                .apiKey(provider.getApiKey())
                .baseUrl(baseUrl)
                .modelName(model)
                .titleMetadataKey(google.getTitleMetadataKey());
        String taskType = google.getTaskType();
        if (taskType != null && !taskType.isBlank()) {
            builder.taskType(GoogleAiEmbeddingModel.TaskType.valueOf(taskType.trim().toUpperCase(Locale.ROOT)));
        }
        return builder.build();
    }

    private static String resolveBaseUrl(AiAdapterProperties.Provider provider, Environment environment) {
        if (provider.getType() == AiAdapterProperties.ProviderType.OPENAI) {
            String configured = environment.getProperty("spring.ai.openai.base-url");
            if (StringUtils.isNotBlank(configured)) {
                return configured;
            }
        }
        if (StringUtils.isNotBlank(provider.getBaseUrl())) {
            return provider.getBaseUrl();
        }
        return switch (provider.getType()) {
            case OPENAI -> "https://api.openai.com/v1";
            case OLLAMA -> "http://localhost:11434";
            case GOOGLE_AI_GEMINI -> "https://generativelanguage.googleapis.com/v1";
            default -> null;
        };
    }

    private static String requireModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model name must be provided for embedding configuration");
        }
        return model;
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.isNotBlank(value)) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private static GoogleAiEmbeddingModel.TaskType parseTaskType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return GoogleAiEmbeddingModel.TaskType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
