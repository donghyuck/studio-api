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

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.autoconfigure.adapter.SpringAiEmbeddingAdapter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;

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
        return switch (provider.getType()) {
            case OPENAI -> createSpringAiEmbeddingPort(springAiEmbeddingModelProvider);
            case OLLAMA -> createOllamaSpringAiEmbeddingPort(environment);
            case GOOGLE_AI_GEMINI -> createGoogleSpringAiEmbeddingPort(provider, environment);
            default -> throw new IllegalArgumentException("Unsupported embedding provider: " + provider.getType());
        };
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

    private static EmbeddingPort createGoogleSpringAiEmbeddingPort(AiAdapterProperties.Provider provider, Environment environment) {
        String apiKey = requireText(environment.getProperty("spring.ai.google.genai.embedding.api-key"),
                "spring.ai.google.genai.embedding.api-key must be configured for GOOGLE_AI_GEMINI embedding provider");
        String model = requireText(environment.getProperty("spring.ai.google.genai.embedding.text.options.model"),
                "spring.ai.google.genai.embedding.text.options.model must be configured for GOOGLE_AI_GEMINI embedding provider");
        AiAdapterProperties.GoogleEmbeddingOptions google = provider.getGoogleEmbedding();
        org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails connectionDetails =
                org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails.builder()
                        .apiKey(apiKey)
                        .projectId(environment.getProperty("spring.ai.google.genai.embedding.project-id"))
                        .location(environment.getProperty("spring.ai.google.genai.embedding.location"))
                        .build();
        org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions options =
                org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.builder()
                        .model(model)
                        .taskType(parseSpringAiGoogleTaskType(google.getTaskType()))
                        .build();
        org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel embeddingModel =
                new org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel(connectionDetails, options);
        return new SpringAiEmbeddingAdapter(embeddingModel);
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.isNotBlank(value)) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private static org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.TaskType parseSpringAiGoogleTaskType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.TaskType.valueOf(
                value.trim().toUpperCase(Locale.ROOT));
    }
}
