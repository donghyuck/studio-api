package studio.one.platform.ai.adapters.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import studio.one.platform.ai.adapters.embedding.LangChainEmbeddingAdapter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

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
    @ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "ollama")
    public EmbeddingPort ollamaEmbeddingPort(AiAdapterProperties properties) {
        AiAdapterProperties.OllamaProperties ollama = properties.getOllama();
        EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(ollama.getBaseUrl())
                .modelName(ollama.getModel())
                .build();
        return new LangChainEmbeddingAdapter(embeddingModel);
    }
}
