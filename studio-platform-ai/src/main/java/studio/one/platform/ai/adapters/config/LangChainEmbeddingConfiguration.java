package studio.one.platform.ai.adapters.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import studio.one.platform.ai.adapters.embedding.LangChainEmbeddingAdapter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

import java.util.Locale;

@Configuration
@EnableConfigurationProperties(AiAdapterProperties.class)
public class LangChainEmbeddingConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "openai", matchIfMissing = true)
    public EmbeddingPort openAiEmbeddingPort(AiAdapterProperties properties) {
        AiAdapterProperties.OpenAiProperties openai = properties.getOpenai();
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(openai.getApiKey())
                .baseUrl(openai.getBaseUrl())
                .modelName(openai.getModel())
                .build();
        return new LangChainEmbeddingAdapter(embeddingModel);
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingPort.class)
    @ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "google-ai-gemini")
    public EmbeddingPort googleAiGeminiEmbeddingPort(AiAdapterProperties properties) {
        AiAdapterProperties.GoogleAiGeminiProperties google = properties.getGoogleAiGemini();
        GoogleAiEmbeddingModel.GoogleAiEmbeddingModelBuilder builder = GoogleAiEmbeddingModel.builder()
                .apiKey(google.getApiKey())
                .baseUrl(google.getBaseUrl())
                .modelName(google.getEmbeddingModel())
                .titleMetadataKey(google.getTitleMetadataKey());
        GoogleAiEmbeddingModel.TaskType taskType = parseTaskType(google.getTaskType());
        if (taskType != null) {
            builder.taskType(taskType);
        }
        EmbeddingModel embeddingModel = builder.build();
        return new LangChainEmbeddingAdapter(embeddingModel);
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingPort.class)
    @ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "ollama")
    public EmbeddingPort ollamaEmbeddingPort(AiAdapterProperties properties) {
        AiAdapterProperties.OllamaProperties ollama = properties.getOllama();
        EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(ollama.getBaseUrl())
                .modelName(ollama.getModel())
                .build();
        return new LangChainEmbeddingAdapter(embeddingModel);
    }

    private static GoogleAiEmbeddingModel.TaskType parseTaskType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return GoogleAiEmbeddingModel.TaskType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
