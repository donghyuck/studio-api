package studio.one.platform.ai.adapters.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import studio.one.platform.ai.core.AiProvider;

import java.util.Objects;

@ConfigurationProperties(prefix = "ai")
public class AiAdapterProperties {

    private AiProvider provider = AiProvider.OPENAI;

    @NestedConfigurationProperty
    private final OpenAiProperties openai = new OpenAiProperties();

    @NestedConfigurationProperty
    private final OllamaProperties ollama = new OllamaProperties();

    public AiProvider getProvider() {
        return provider;
    }

    public void setProvider(AiProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    public OpenAiProperties getOpenai() {
        return openai;
    }

    public OllamaProperties getOllama() {
        return ollama;
    }

    public static class OpenAiProperties {
        private String apiKey;
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "text-embedding-3-small";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class OllamaProperties {
        private String baseUrl = "http://localhost:11434";
        private String model = "nomic-embed-text";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
