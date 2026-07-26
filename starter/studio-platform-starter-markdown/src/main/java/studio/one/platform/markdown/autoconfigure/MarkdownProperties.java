package studio.one.platform.markdown.autoconfigure;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

@ConfigurationProperties("studio.markdown")
public class MarkdownProperties {
    private boolean enabled;
    private String maxSourceBytes = "64MB";
    private String pandocVersion = "pandoc";
    private List<String> pandocFormats = new ArrayList<>(List.of("docx", "html"));
    private boolean fallbackToNativeOnPandocFailure = true;
    private Path resultCacheDir = Path.of("var/lib/app/markdown");
    private String textractVersion = "native";
    private final Web web = new Web();
    private final Metadata metadata = new Metadata();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxSourceBytes() {
        return parseToBytes(maxSourceBytes, "studio.markdown.max-source-bytes");
    }

    public void setMaxSourceBytes(String maxSourceBytes) {
        this.maxSourceBytes = maxSourceBytes;
    }

    public String getPandocVersion() {
        return pandocVersion;
    }

    public void setPandocVersion(String pandocVersion) {
        this.pandocVersion = pandocVersion;
    }

    public List<String> getPandocFormats() {
        return pandocFormats;
    }

    public void setPandocFormats(List<String> pandocFormats) {
        this.pandocFormats = pandocFormats == null ? new ArrayList<>() : new ArrayList<>(pandocFormats);
    }

    public boolean isFallbackToNativeOnPandocFailure() {
        return fallbackToNativeOnPandocFailure;
    }

    public void setFallbackToNativeOnPandocFailure(boolean fallbackToNativeOnPandocFailure) {
        this.fallbackToNativeOnPandocFailure = fallbackToNativeOnPandocFailure;
    }

    public Path getResultCacheDir() {
        return resultCacheDir;
    }

    public void setResultCacheDir(Path resultCacheDir) {
        this.resultCacheDir = resultCacheDir == null ? Path.of("var/lib/app/markdown") : resultCacheDir;
    }

    public String getTextractVersion() {
        return textractVersion;
    }

    public void setTextractVersion(String textractVersion) {
        this.textractVersion = textractVersion;
    }

    public Web getWeb() {
        return web;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public static class Web {
        private String basePath = "/api/markdown-documents";

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }
    }

    public static class Metadata {
        private String llmDeploymentId = "chat-default";
        private String promptResource = "classpath:prompts/document-metadata.v1.prompt";
        private double typeConfidenceThreshold = 0.80d;
        private double fieldConfidenceThreshold = 0.85d;
        private int maxPages = 5;
        private int maxBlocks = 40;
        private int maxCharacters = 24_000;

        public String getLlmDeploymentId() {
            return llmDeploymentId;
        }

        public void setLlmDeploymentId(String llmDeploymentId) {
            this.llmDeploymentId = llmDeploymentId;
        }

        public String getPromptResource() {
            return promptResource;
        }

        public void setPromptResource(String promptResource) {
            this.promptResource = promptResource;
        }

        public double getTypeConfidenceThreshold() {
            return typeConfidenceThreshold;
        }

        public void setTypeConfidenceThreshold(double typeConfidenceThreshold) {
            this.typeConfidenceThreshold = probability(typeConfidenceThreshold, "type-confidence-threshold");
        }

        public double getFieldConfidenceThreshold() {
            return fieldConfidenceThreshold;
        }

        public void setFieldConfidenceThreshold(double fieldConfidenceThreshold) {
            this.fieldConfidenceThreshold = probability(fieldConfidenceThreshold, "field-confidence-threshold");
        }

        public int getMaxPages() {
            return maxPages;
        }

        public void setMaxPages(int maxPages) {
            this.maxPages = positive(maxPages, "max-pages");
        }

        public int getMaxBlocks() {
            return maxBlocks;
        }

        public void setMaxBlocks(int maxBlocks) {
            this.maxBlocks = positive(maxBlocks, "max-blocks");
        }

        public int getMaxCharacters() {
            return maxCharacters;
        }

        public void setMaxCharacters(int maxCharacters) {
            this.maxCharacters = positive(maxCharacters, "max-characters");
        }

        private static double probability(double value, String name) {
            if (value < 0.0d || value > 1.0d) {
                throw new IllegalArgumentException("studio.markdown.metadata." + name + " must be between 0 and 1");
            }
            return value;
        }

        private static int positive(int value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException("studio.markdown.metadata." + name + " must be positive");
            }
            return value;
        }
    }

    static int parseToBytes(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }

        String normalized = normalizeDataSize(value);
        DataSize dataSize;
        try {
            dataSize = DataSize.parse(normalized, DataUnit.BYTES);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(propertyName
                    + " must be a positive data size such as 64M, 64MB, or 67108864", ex);
        }

        long bytes = dataSize.toBytes();
        if (bytes <= 0 || bytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(propertyName + " must be between 1B and "
                    + Integer.MAX_VALUE + "B");
        }
        return Math.toIntExact(bytes);
    }

    private static String normalizeDataSize(String value) {
        String trimmed = value.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.endsWith("K") || upper.endsWith("M") || upper.endsWith("G") || upper.endsWith("T")) {
            return trimmed + "B";
        }
        return trimmed;
    }
}
