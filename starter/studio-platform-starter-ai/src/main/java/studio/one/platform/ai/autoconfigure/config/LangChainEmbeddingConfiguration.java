package studio.one.platform.ai.autoconfigure.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
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
                                                    ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> springAiEmbeddingModelProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        Map<String, EmbeddingPort> ports = new LinkedHashMap<>();
        for (Map.Entry<String, AiAdapterProperties.Provider> entry : properties.getProviders().entrySet()) {
            AiAdapterProperties.Provider provider = entry.getValue();
            log.debug("checking <{}> : provider - {}, embedding - {}", entry.getKey(), provider.isEnabled(), provider.getEmbedding().isEnabled());
            if (!provider.isEnabled() || !provider.getEmbedding().isEnabled()) {
                continue;
            }
            if (isSpringAiSourceProvider(entry.getKey(), properties)) {
                ports.put(entry.getKey(), createSpringAiEmbeddingPort(springAiEmbeddingModelProvider));
            } else {
                ports.put(entry.getKey(), createEmbedding(provider, i18n));
            }
            registerSpringAiEmbeddingPort(ports, entry.getKey(), provider, properties, springAiEmbeddingModelProvider);
        }
        return ports;
    }

    private static void registerSpringAiEmbeddingPort(Map<String, EmbeddingPort> ports, String providerName,
            AiAdapterProperties.Provider provider, AiAdapterProperties properties,
            ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> springAiEmbeddingModelProvider) {
        if (!properties.getSpringAi().isEnabled() || provider.getType() != AiAdapterProperties.ProviderType.OPENAI) {
            return;
        }
        if (!isSpringAiSourceProvider(providerName, properties)) {
            return;
        }
        String alias = springAiAlias(providerName, properties);
        if (properties.getProviders().containsKey(alias) && !providerName.equals(alias)) {
            throw new IllegalStateException("Spring AI alias provider collides with configured provider: " + alias);
        }
        if (ports.containsKey(alias)) {
            throw new IllegalStateException("Spring AI alias provider already exists: " + alias);
        }
        ports.put(alias, createSpringAiEmbeddingPort(springAiEmbeddingModelProvider));
    }

    private static String springAiAlias(String providerName, AiAdapterProperties properties) {
        String suffix = properties.getSpringAi().getProviderSuffix();
        if (suffix == null || suffix.isBlank()) {
            throw new IllegalArgumentException("studio.ai.spring-ai.provider-suffix must not be blank");
        }
        return providerName + suffix;
    }

    private static boolean isSpringAiSourceProvider(String providerName, AiAdapterProperties properties) {
        String sourceProvider = properties.getSpringAi().getSourceProvider();
        if (sourceProvider == null || sourceProvider.isBlank()) {
            return false;
        }
        return providerName.equalsIgnoreCase(sourceProvider);
    }

    private static EmbeddingPort createSpringAiEmbeddingPort(
            ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> springAiEmbeddingModelProvider) {
        org.springframework.ai.embedding.EmbeddingModel embeddingModel = springAiEmbeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            throw new IllegalStateException("Spring AI embedding model bean is required when studio.ai.spring-ai.enabled=true");
        }
        return new SpringAiEmbeddingAdapter(embeddingModel);
    }

    private static EmbeddingPort createEmbedding(AiAdapterProperties.Provider provider, I18n i18n) {
        log.debug("Creating Embedding Port by  {}", provider );
        String model = requireModel(provider.getEmbedding().getModel());
        String baseUrl = resolveBaseUrl(provider);
        EmbeddingModel embeddingModel = switch (provider.getType()) {
            case OPENAI -> OpenAiEmbeddingModel.builder()
                    .apiKey(provider.getApiKey())
                    .baseUrl(baseUrl)
                    .modelName(model)
                    .build();
            case OLLAMA -> OllamaEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(model)
                    .build();
            case GOOGLE_AI_GEMINI -> buildGoogleEmbedding(provider, baseUrl, model);
            default -> throw new IllegalArgumentException("Unsupported embedding provider: " + provider.getType());
        };
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(EmbeddingModel.class, true),
                LogUtils.green(embeddingModel.getClass(), true),
                LogUtils.red(State.CREATED.toString())));
        return new LangChainEmbeddingAdapter(embeddingModel);
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

    private static String resolveBaseUrl(AiAdapterProperties.Provider provider) {
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

    private static GoogleAiEmbeddingModel.TaskType parseTaskType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return GoogleAiEmbeddingModel.TaskType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
