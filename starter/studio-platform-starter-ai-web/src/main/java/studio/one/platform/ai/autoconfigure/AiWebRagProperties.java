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
        private final ExpansionProperties expansion = new ExpansionProperties();

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

        public ExpansionProperties getExpansion() {
            return expansion;
        }
    }

    public static class ExpansionProperties {
        private boolean enabled = true;
        private int candidateMultiplier = 4;
        private int previousWindow = 1;
        private int nextWindow = 1;
        private boolean includeParentContent = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCandidateMultiplier() {
            return candidateMultiplier;
        }

        public void setCandidateMultiplier(int candidateMultiplier) {
            this.candidateMultiplier = Math.max(1, candidateMultiplier);
        }

        public int getPreviousWindow() {
            return previousWindow;
        }

        public void setPreviousWindow(int previousWindow) {
            this.previousWindow = Math.max(0, previousWindow);
        }

        public int getNextWindow() {
            return nextWindow;
        }

        public void setNextWindow(int nextWindow) {
            this.nextWindow = Math.max(0, nextWindow);
        }

        public boolean isIncludeParentContent() {
            return includeParentContent;
        }

        public void setIncludeParentContent(boolean includeParentContent) {
            this.includeParentContent = includeParentContent;
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
