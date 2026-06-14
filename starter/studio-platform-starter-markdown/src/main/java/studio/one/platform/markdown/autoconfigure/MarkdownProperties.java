package studio.one.platform.markdown.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("studio.markdown")
public class MarkdownProperties {
    private boolean enabled;
    private int maxSourceBytes = 25 * 1024 * 1024;
    private String pandocVersion = "pandoc";
    private String textractVersion = "native";
    private final Web web = new Web();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxSourceBytes() {
        return maxSourceBytes;
    }

    public void setMaxSourceBytes(int maxSourceBytes) {
        this.maxSourceBytes = maxSourceBytes;
    }

    public String getPandocVersion() {
        return pandocVersion;
    }

    public void setPandocVersion(String pandocVersion) {
        this.pandocVersion = pandocVersion;
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
}
