package studio.one.platform.ai.autoconfigure.config;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.ai.core.AiProvider;
import studio.one.platform.constant.PropertyKeys;

/**
 * Studio AI inspired configuration binding allowing multiple providers to be
 * defined.
 */
@ConfigurationProperties(prefix = PropertyKeys.AI.PREFIX)
public class AiAdapterProperties {

    private boolean enabled = false;
    private String defaultProvider = AiProvider.OPENAI.name().toLowerCase();
    private final OpenAiProperties openai = new OpenAiProperties();
    private final OllamaProperties ollama = new OllamaProperties();
    private final GoogleAiGeminiProperties googleAiGemini = new GoogleAiGeminiProperties();
    private Endpoints endpoints = new Endpoints();

    public Endpoints getEndpoints(){
        return endpoints;
    }

    public void setEndpoints(Endpoints endpoints){
        this.endpoints = endpoints;
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = Objects.requireNonNull(defaultProvider, "defaultProvider");
    }

    public OpenAiProperties getOpenai() {
        return openai;
    }

    public OllamaProperties getOllama() {
        return ollama;
    }

    public GoogleAiGeminiProperties getGoogleAiGemini() {
        return googleAiGemini;
    }

    @Getter @Setter
    public static class Endpoints { 
        private boolean enabled = false;
        private String basePath = "/api/ai";
    }

    @Getter @Setter
    public static class ProviderProperties {
        private boolean enabled = false;
        private String baseUrl;
        private boolean debug = false;

        // public boolean isEnabled() {
        //     return enabled;
        // }

        // public void setEnabled(boolean enabled) {
        //     this.enabled = enabled;
        // }

        // public String getBaseUrl() {
        //     return baseUrl;
        // }

        // public void setBaseUrl(String baseUrl) {
        //     this.baseUrl = baseUrl;
        // }
    }

    @Getter @Setter
    public static class Options {
        private String model;

        // public String getModel() {
        //     return model;
        // }

        // public void setModel(String model) {
        //     this.model = model;
        // }
    }

    public static class OpenAiProperties extends ProviderProperties {

        private String apiKey;
        private final ChatProperties chat = new ChatProperties();
        private final EmbeddingProperties embedding = new EmbeddingProperties();

        public OpenAiProperties() {
            setBaseUrl("https://api.openai.com/v1");
            chat.getOptions().setModel("gpt-4o-mini");
            embedding.getOptions().setModel("text-embedding-3-small");
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public ChatProperties getChat() {
            return chat;
        }

        public EmbeddingProperties getEmbedding() {
            return embedding;
        }

        public static class ChatProperties {
            private boolean enabled = true;
            private final Options options = new Options();

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public Options getOptions() {
                return options;
            }
        }

        public static class EmbeddingProperties {
            private boolean enabled = true;
            private final Options options = new Options();

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public Options getOptions() {
                return options;
            }
        }
    }

    public static class OllamaProperties extends ProviderProperties {
        private final EmbeddingProperties embedding = new EmbeddingProperties();

        public OllamaProperties() {
            setBaseUrl("http://localhost:11434");
            embedding.getOptions().setModel("nomic-embed-text");
        }

        public EmbeddingProperties getEmbedding() {
            return embedding;
        }

        public static class EmbeddingProperties {
            private boolean enabled = false;
            private final Options options = new Options();

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public Options getOptions() {
                return options;
            }
        }
    }

    public static class GoogleAiGeminiProperties extends ProviderProperties {
        private String apiKey;
        private final ChatProperties chat = new ChatProperties();
        private final EmbeddingProperties embedding = new EmbeddingProperties();

        public GoogleAiGeminiProperties() {
            //setBaseUrl("https://generativelanguage.googleapis.com/v1");
            //chat.getOptions().setModel("models/chat-bison-001");
            //embedding.getOptions().setModel("textembedding-gecko-001");
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public ChatProperties getChat() {
            return chat;
        }

        public EmbeddingProperties getEmbedding() {
            return embedding;
        }

        public static class ChatProperties {
            private boolean enabled = false;
            private final Options options = new Options();

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public Options getOptions() {
                return options;
            }
        }

        public static class EmbeddingProperties {
            private boolean enabled = false;
            private final Options options = new Options();
            private String taskType = "RETRIEVAL_DOCUMENT";
            private String titleMetadataKey = "title";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public Options getOptions() {
                return options;
            }

            public String getTaskType() {
                return taskType;
            }

            public void setTaskType(String taskType) {
                this.taskType = taskType;
            }

            public String getTitleMetadataKey() {
                return titleMetadataKey;
            }

            public void setTitleMetadataKey(String titleMetadataKey) {
                this.titleMetadataKey = titleMetadataKey;
            }
        }
    }
}
