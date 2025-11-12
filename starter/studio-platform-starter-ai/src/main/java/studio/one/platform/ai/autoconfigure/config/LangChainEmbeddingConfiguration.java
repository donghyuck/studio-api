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
            ObjectProvider<I18n> i18nProvider) { 

        I18n i18n = I18nUtils.resolve(i18nProvider);
        Map<String, EmbeddingPort> ports = new LinkedHashMap<>();
        AiAdapterProperties.OpenAiProperties openai = properties.getOpenai();
        if (openai.isEnabled() && openai.getEmbedding().isEnabled()) {
            ports.put("openai", createOpenAiEmbedding(openai, i18n));
        }
        AiAdapterProperties.OllamaProperties ollama = properties.getOllama();
        if (ollama.isEnabled() && ollama.getEmbedding().isEnabled()) {
            ports.put("ollama", createOllamaEmbedding(ollama, i18n));
        }
        AiAdapterProperties.GoogleAiGeminiProperties google = properties.getGoogleAiGemini();
        if (google.isEnabled() && google.getEmbedding().isEnabled()) {
            ports.put("google-ai-gemini", createGoogleAiEmbedding(google, i18n));
        }
        return ports;
    }

    private static EmbeddingPort createOpenAiEmbedding(AiAdapterProperties.OpenAiProperties openai, I18n i18n) {
        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .apiKey(openai.getApiKey())
                .baseUrl(openai.getBaseUrl())
                .modelName(requireModel(openai.getEmbedding().getOptions().getModel()))
                .build();

        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(EmbeddingModel.class, true),
                LogUtils.green(OpenAiEmbeddingModel.class, true),
                LogUtils.red(State.CREATED.toString())));
        return new LangChainEmbeddingAdapter(model);
    }

    private static EmbeddingPort createOllamaEmbedding(AiAdapterProperties.OllamaProperties ollama, I18n i18n) {
        EmbeddingModel model = OllamaEmbeddingModel.builder()
                .baseUrl(ollama.getBaseUrl())
                .modelName(requireModel(ollama.getEmbedding().getOptions().getModel()))
                .build();

        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(EmbeddingModel.class, true),
                LogUtils.green(OllamaEmbeddingModel.class, true),
                LogUtils.red(State.CREATED.toString())));
        return new LangChainEmbeddingAdapter(model);
    }

    private static EmbeddingPort createGoogleAiEmbedding(AiAdapterProperties.GoogleAiGeminiProperties google,
            I18n i18n) {
        AiAdapterProperties.GoogleAiGeminiProperties.EmbeddingProperties embedding = google.getEmbedding(); 
        GoogleAiEmbeddingModel.GoogleAiEmbeddingModelBuilder builder = GoogleAiEmbeddingModel.builder()
                .apiKey(google.getApiKey()) 
                .modelName(requireModel(embedding.getOptions().getModel())); 
        
        if( StringUtils.isNotEmpty(google.getBaseUrl()))
                builder.baseUrl(google.getBaseUrl());

        if( StringUtils.isNotEmpty(embedding.getTitleMetadataKey()))
                builder.titleMetadataKey(embedding.getTitleMetadataKey()); 

        GoogleAiEmbeddingModel.TaskType taskType = parseTaskType(embedding.getTaskType());
        if (taskType != null) {
            builder.taskType(taskType);
        }

        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(EmbeddingModel.class, true),
                LogUtils.green(GoogleAiEmbeddingModel.class, true),
                LogUtils.red(State.CREATED.toString())));
        return new LangChainEmbeddingAdapter(builder.build());
    }

    private static String requireModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model name must be provided");
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
