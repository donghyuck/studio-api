package studio.one.platform.ai.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.AI.Endpoints.PREFIX + ".rag")
public class AiWebRagProperties {

    private final ContextProperties context = new ContextProperties();
    private final DiagnosticsProperties diagnostics = new DiagnosticsProperties();
    private final ChunkPreviewProperties chunkPreview = new ChunkPreviewProperties();
    private final RetrievalProperties retrieval = new RetrievalProperties();

    public ContextProperties getContext() {
        return context;
    }

    public DiagnosticsProperties getDiagnostics() {
        return diagnostics;
    }

    public ChunkPreviewProperties getChunkPreview() {
        return chunkPreview;
    }

    public RetrievalProperties getRetrieval() {
        return retrieval;
    }

    public static class ContextProperties {
        private int maxChunks = 8;
        private int maxChars = 12_000;
        private int maxChunkChars = 2_000;
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

        public int getMaxChunkChars() {
            return maxChunkChars;
        }

        public void setMaxChunkChars(int maxChunkChars) {
            this.maxChunkChars = Math.max(1, maxChunkChars);
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
        private int maxCandidates = 100;
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

        public int getMaxCandidates() {
            return maxCandidates;
        }

        public void setMaxCandidates(int maxCandidates) {
            this.maxCandidates = Math.max(1, maxCandidates);
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

    public static class ChunkPreviewProperties {
        private boolean enabled = true;
        private int maxInputChars = 200_000;
        private int maxPreviewChunks = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxInputChars() {
            return maxInputChars;
        }

        public void setMaxInputChars(int maxInputChars) {
            this.maxInputChars = Math.max(1, maxInputChars);
        }

        public int getMaxPreviewChunks() {
            return maxPreviewChunks;
        }

        public void setMaxPreviewChunks(int maxPreviewChunks) {
            this.maxPreviewChunks = Math.max(1, maxPreviewChunks);
        }
    }

    public static class RetrievalProperties {
        private String defaultStrategy = "hybrid";
        private int structureTopK = 5;
        private int ideaBlockTopK = 5;
        private int finalTopK = 5;
        private boolean dedupe = true;
        private double distilledScoreBoost = 0.0d;
        private double minRecommendationHitRate = 0.3d;
        private double minRecommendationMrr = 0.2d;

        public String getDefaultStrategy() {
            return defaultStrategy;
        }

        public void setDefaultStrategy(String defaultStrategy) {
            this.defaultStrategy = defaultStrategy;
        }

        public int getStructureTopK() {
            return structureTopK;
        }

        public void setStructureTopK(int structureTopK) {
            this.structureTopK = Math.max(1, structureTopK);
        }

        public int getIdeaBlockTopK() {
            return ideaBlockTopK;
        }

        public void setIdeaBlockTopK(int ideaBlockTopK) {
            this.ideaBlockTopK = Math.max(1, ideaBlockTopK);
        }

        public int getFinalTopK() {
            return finalTopK;
        }

        public void setFinalTopK(int finalTopK) {
            this.finalTopK = Math.max(1, finalTopK);
        }

        public boolean isDedupe() {
            return dedupe;
        }

        public void setDedupe(boolean dedupe) {
            this.dedupe = dedupe;
        }

        public double getDistilledScoreBoost() {
            return distilledScoreBoost;
        }

        public void setDistilledScoreBoost(double distilledScoreBoost) {
            this.distilledScoreBoost = Math.max(0.0d, Math.min(1.0d, distilledScoreBoost));
        }

        public double getMinRecommendationHitRate() {
            return minRecommendationHitRate;
        }

        public void setMinRecommendationHitRate(double minRecommendationHitRate) {
            this.minRecommendationHitRate = Math.max(0.0d, Math.min(1.0d, minRecommendationHitRate));
        }

        public double getMinRecommendationMrr() {
            return minRecommendationMrr;
        }

        public void setMinRecommendationMrr(double minRecommendationMrr) {
            this.minRecommendationMrr = Math.max(0.0d, Math.min(1.0d, minRecommendationMrr));
        }
    }
}
