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

    public static class Web {
        private String basePath = "/api/markdown-documents";

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
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
