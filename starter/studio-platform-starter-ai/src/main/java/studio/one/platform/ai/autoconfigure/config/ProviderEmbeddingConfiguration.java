package studio.one.platform.ai.autoconfigure.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.autoconfigure.adapter.SpringAiEmbeddingAdapter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiAdapterProperties.class)
@Slf4j
public class ProviderEmbeddingConfiguration {

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
        EmbeddingPort embeddingPort = switch (provider.getType()) {
            case OPENAI -> createSpringAiEmbeddingPort(springAiEmbeddingModelProvider);
            case OLLAMA -> createOllamaEmbeddingPort(provider, environment);
            case GOOGLE_AI_GEMINI -> createGoogleEmbeddingPort(provider, resolveBaseUrl(provider, environment));
            default -> throw new IllegalArgumentException("Unsupported embedding provider: " + provider.getType());
        };
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(EmbeddingPort.class, true),
                LogUtils.green(embeddingPort.getClass(), true),
                LogUtils.red(State.CREATED.toString())));
        return embeddingPort;
    }

    private static EmbeddingPort createOllamaEmbeddingPort(AiAdapterProperties.Provider provider, Environment environment) {
        OllamaEmbeddingOptions options = OllamaEmbeddingOptions.builder()
                .model(requireModel(provider.getEmbedding().getModel()))
                .build();
        OllamaEmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                .ollamaApi(OllamaApi.builder()
                        .baseUrl(resolveBaseUrl(provider, environment))
                        .build())
                .defaultOptions(options)
                .build();
        return new SpringAiEmbeddingAdapter(embeddingModel);
    }

    private static EmbeddingPort createGoogleEmbeddingPort(AiAdapterProperties.Provider provider, String baseUrl) {
        AiAdapterProperties.GoogleEmbeddingOptions google = provider.getGoogleEmbedding();
        GoogleGenAiTextEmbeddingOptions.Builder optionsBuilder = GoogleGenAiTextEmbeddingOptions.builder()
                .model(requireModel(provider.getEmbedding().getModel()));
        String taskType = google.getTaskType();
        if (taskType != null && !taskType.isBlank()) {
            optionsBuilder.taskType(GoogleGenAiTextEmbeddingOptions.TaskType.valueOf(taskType.trim().toUpperCase(Locale.ROOT)));
        }
        if (StringUtils.isNotBlank(google.getTitleMetadataKey())) {
            optionsBuilder.title(google.getTitleMetadataKey());
        }
        Client.Builder clientBuilder = Client.builder()
                .apiKey(provider.getApiKey());
        if (StringUtils.isNotBlank(baseUrl)) {
            clientBuilder.httpOptions(HttpOptions.builder()
                    .baseUrl(baseUrl)
                    .build());
        }
        GoogleGenAiEmbeddingConnectionDetails connectionDetails = GoogleGenAiEmbeddingConnectionDetails.builder()
                .apiKey(provider.getApiKey())
                .genAiClient(clientBuilder.build())
                .build();
        return new SpringAiEmbeddingAdapter(new GoogleGenAiTextEmbeddingModel(connectionDetails, optionsBuilder.build()));
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
            case GOOGLE_AI_GEMINI -> "https://generativelanguage.googleapis.com/v1beta";
            default -> null;
        };
    }

    private static String requireModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model name must be provided for embedding configuration");
        }
        return model;
    }
}
