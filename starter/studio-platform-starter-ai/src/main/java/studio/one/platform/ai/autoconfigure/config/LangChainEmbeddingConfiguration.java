package studio.one.platform.ai.autoconfigure.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import studio.one.platform.ai.adapters.embedding.LangChainEmbeddingAdapter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class LangChainEmbeddingConfiguration {

    @Bean
    public Map<String, EmbeddingPort> embeddingPorts(AiAdapterProperties properties) {
        Map<String, EmbeddingPort> ports = new LinkedHashMap<>();
        AiAdapterProperties.OpenAiProperties openai = properties.getOpenai();
        if (openai.isEnabled() && openai.getEmbedding().isEnabled()) {
            ports.put("openai", createOpenAiEmbedding(openai));
        }
        AiAdapterProperties.OllamaProperties ollama = properties.getOllama();
        if (ollama.isEnabled() && ollama.getEmbedding().isEnabled()) {
            ports.put("ollama", createOllamaEmbedding(ollama));
        }
        AiAdapterProperties.GoogleAiGeminiProperties google = properties.getGoogleAiGemini();
        if (google.isEnabled() && google.getEmbedding().isEnabled()) {
            ports.put("google-ai-gemini", createGoogleAiEmbedding(google));
        }
        return ports;
    }

    private static EmbeddingPort createOpenAiEmbedding(AiAdapterProperties.OpenAiProperties openai) {
        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .apiKey(openai.getApiKey())
                .baseUrl(openai.getBaseUrl())
                .modelName(requireModel(openai.getEmbedding().getOptions().getModel()))
                .build();
        return new LangChainEmbeddingAdapter(model);
    }

    private static EmbeddingPort createOllamaEmbedding(AiAdapterProperties.OllamaProperties ollama) {
        EmbeddingModel model = OllamaEmbeddingModel.builder()
                .baseUrl(ollama.getBaseUrl())
                .modelName(requireModel(ollama.getEmbedding().getOptions().getModel()))
                .build();
        return new LangChainEmbeddingAdapter(model);
    }

    private static EmbeddingPort createGoogleAiEmbedding(AiAdapterProperties.GoogleAiGeminiProperties google) {
        AiAdapterProperties.GoogleAiGeminiProperties.EmbeddingProperties embedding = google.getEmbedding();
        GoogleAiEmbeddingModel.GoogleAiEmbeddingModelBuilder builder = GoogleAiEmbeddingModel.builder()
                .apiKey(google.getApiKey())
                .baseUrl(google.getBaseUrl())
                .modelName(requireModel(embedding.getOptions().getModel()))
                .titleMetadataKey(embedding.getTitleMetadataKey());
        GoogleAiEmbeddingModel.TaskType taskType = parseTaskType(embedding.getTaskType());
        if (taskType != null) {
            builder.taskType(taskType);
        }
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
