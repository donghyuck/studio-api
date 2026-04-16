package studio.one.platform.ai.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.AI.Endpoints.PREFIX + ".rag")
public class AiWebRagProperties {

    private final ContextProperties context = new ContextProperties();
    private final DiagnosticsProperties diagnostics = new DiagnosticsProperties();

    public ContextProperties getContext() {
        return context;
    }

    public DiagnosticsProperties getDiagnostics() {
        return diagnostics;
    }

    public static class ContextProperties {
        private int maxChunks = 8;
        private int maxChars = 12_000;
        private boolean includeScores = true;

        public int getMaxChunks() {
            return maxChunks;
        }

        public void setMaxChunks(int maxChunks) {
            this.maxChunks = maxChunks;
        }

        public int getMaxChars() {
            return maxChars;
        }

        public void setMaxChars(int maxChars) {
            this.maxChars = maxChars;
        }

        public boolean isIncludeScores() {
            return includeScores;
        }

        public void setIncludeScores(boolean includeScores) {
            this.includeScores = includeScores;
        }
    }

    public static class DiagnosticsProperties {
        private boolean allowClientDebug = false;

        public boolean isAllowClientDebug() {
            return allowClientDebug;
        }

        public void setAllowClientDebug(boolean allowClientDebug) {
            this.allowClientDebug = allowClientDebug;
        }
    }
}
