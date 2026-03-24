package studio.one.platform.ai.autoconfigure.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import studio.one.platform.constant.PropertyKeys;

/**
 * Allows defining arbitrarily many AI providers using a {@code type}
 * discriminator.
 */
@ConfigurationProperties(prefix = PropertyKeys.AI.PREFIX)
@Getter
@Setter
public class AiAdapterProperties {

    private boolean enabled = false;
    private String defaultProvider;
    private final Map<String, Provider> providers = new LinkedHashMap<>();
    private Endpoints endpoints = new Endpoints();

    @Getter
    @Setter
    @ToString
    public static final class Provider {

        private ProviderType type;
        private boolean enabled = true;
        private String apiKey;
        private String baseUrl;
        private final Channel chat = new Channel();
        private final Channel embedding = new Channel();
        private final GoogleEmbeddingOptions googleEmbedding = new GoogleEmbeddingOptions();
    }

    @Getter
    @Setter
    @ToString
    public static class Endpoints {
        private boolean enabled = false;
        private String basePath = "/api/ai";
    }

    @Getter
    @Setter
    @ToString
    public static final class Channel {
        private boolean enabled = false;
        private String model;
    }

    @Getter
    @Setter
    @ToString
    public static final class GoogleEmbeddingOptions {
        private String taskType = "RETRIEVAL_DOCUMENT";
        private String titleMetadataKey = "title";
    }

    public enum ProviderType {
        OPENAI,
        OLLAMA,
        GOOGLE_AI_GEMINI
    }
}
