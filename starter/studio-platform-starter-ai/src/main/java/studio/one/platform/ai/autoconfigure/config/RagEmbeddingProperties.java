package studio.one.platform.ai.autoconfigure.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.AI.PREFIX + ".rag")
public class RagEmbeddingProperties {

    private String defaultEmbeddingProfile;
    private final Map<String, ProfileProperties> embeddingProfiles = new LinkedHashMap<>();

    public String getDefaultEmbeddingProfile() {
        return defaultEmbeddingProfile;
    }

    public void setDefaultEmbeddingProfile(String defaultEmbeddingProfile) {
        this.defaultEmbeddingProfile = defaultEmbeddingProfile;
    }

    public Map<String, ProfileProperties> getEmbeddingProfiles() {
        return embeddingProfiles;
    }

    public static class ProfileProperties {
        private String provider;
        private String model;
        private Integer dimension;
        private List<String> supportedInputTypes = List.of(
                EmbeddingInputType.TEXT.name(),
                EmbeddingInputType.TABLE_TEXT.name(),
                EmbeddingInputType.IMAGE_CAPTION.name(),
                EmbeddingInputType.OCR_TEXT.name());
        private Map<String, Object> metadata = Map.of();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Integer getDimension() {
            return dimension;
        }

        public void setDimension(Integer dimension) {
            this.dimension = dimension;
        }

        public List<String> getSupportedInputTypes() {
            return supportedInputTypes;
        }

        public void setSupportedInputTypes(List<String> supportedInputTypes) {
            this.supportedInputTypes = supportedInputTypes;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }
}
