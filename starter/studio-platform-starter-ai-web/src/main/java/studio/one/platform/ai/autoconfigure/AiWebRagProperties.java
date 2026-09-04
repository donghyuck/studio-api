package studio.one.platform.ai.autoconfigure;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.ai.web.controller.RagAnswerMode;
import studio.one.platform.ai.web.controller.RagSourceScope;

@ConfigurationProperties(prefix = PropertyKeys.AI.Endpoints.PREFIX + ".rag")
public class AiWebRagProperties {

    private final ContextProperties context = new ContextProperties();
    private final DiagnosticsProperties diagnostics = new DiagnosticsProperties();
    private final ChunkPreviewProperties chunkPreview = new ChunkPreviewProperties();
    private final RetrievalProperties retrieval = new RetrievalProperties();
    private final AnswerPolicyProperties answerPolicy = new AnswerPolicyProperties();
    private final SourcePolicyProperties sourcePolicy = new SourcePolicyProperties();
    private final ExternalSourcesProperties externalSources = new ExternalSourcesProperties();

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

    public AnswerPolicyProperties getAnswerPolicy() {
        return answerPolicy;
    }

    public SourcePolicyProperties getSourcePolicy() {
        return sourcePolicy;
    }

    public ExternalSourcesProperties getExternalSources() {
        return externalSources;
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
        private int teamMaxObjectScopes = 32;
        private int teamMaxWorkspaces = 1_000;
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

        public int getTeamMaxObjectScopes() {
            return teamMaxObjectScopes;
        }

        public void setTeamMaxObjectScopes(int teamMaxObjectScopes) {
            this.teamMaxObjectScopes = Math.max(1, Math.min(64, teamMaxObjectScopes));
        }

        public int getTeamMaxWorkspaces() {
            return teamMaxWorkspaces;
        }

        public void setTeamMaxWorkspaces(int teamMaxWorkspaces) {
            this.teamMaxWorkspaces = Math.max(1, Math.min(10_000, teamMaxWorkspaces));
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

    public static class AnswerPolicyProperties {
        private RagAnswerMode defaultMode = RagAnswerMode.GROUNDED_INFERENCE;
        private RagAnswerMode maximumMode = RagAnswerMode.GROUNDED_INFERENCE;
        private boolean clientSelectionEnabled = true;
        private boolean factualListPartialAnswerEnabled;

        public RagAnswerMode getDefaultMode() {
            return defaultMode;
        }

        public void setDefaultMode(RagAnswerMode defaultMode) {
            this.defaultMode = defaultMode;
        }

        public RagAnswerMode getMaximumMode() {
            return maximumMode;
        }

        public void setMaximumMode(RagAnswerMode maximumMode) {
            this.maximumMode = maximumMode;
        }

        public boolean isClientSelectionEnabled() {
            return clientSelectionEnabled;
        }

        public void setClientSelectionEnabled(boolean clientSelectionEnabled) {
            this.clientSelectionEnabled = clientSelectionEnabled;
        }

        public boolean isFactualListPartialAnswerEnabled() {
            return factualListPartialAnswerEnabled;
        }

        public void setFactualListPartialAnswerEnabled(boolean factualListPartialAnswerEnabled) {
            this.factualListPartialAnswerEnabled = factualListPartialAnswerEnabled;
        }
    }

    public static class SourcePolicyProperties {
        private RagSourceScope defaultScope = RagSourceScope.DOCUMENT_ONLY;
        private RagSourceScope maximumScope = RagSourceScope.DOCUMENT_AND_OFFICIAL_EXTERNAL;
        private boolean clientSelectionEnabled;

        public RagSourceScope getDefaultScope() {
            return defaultScope;
        }

        public void setDefaultScope(RagSourceScope defaultScope) {
            this.defaultScope = defaultScope;
        }

        public RagSourceScope getMaximumScope() {
            return maximumScope;
        }

        public void setMaximumScope(RagSourceScope maximumScope) {
            this.maximumScope = maximumScope;
        }

        public boolean isClientSelectionEnabled() {
            return clientSelectionEnabled;
        }

        public void setClientSelectionEnabled(boolean clientSelectionEnabled) {
            this.clientSelectionEnabled = clientSelectionEnabled;
        }
    }

    public static class ExternalSourcesProperties {
        private boolean enabled;
        private String gatewayUrl;
        private String apiKey;
        private Set<String> gatewayAllowedHosts = new LinkedHashSet<>();
        private Set<String> sourceAllowedHosts = new LinkedHashSet<>();
        private Duration timeout = Duration.ofSeconds(8);
        private int maxResults = 8;
        private int maxResponseBytes = 1_000_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getGatewayUrl() {
            return gatewayUrl;
        }

        public void setGatewayUrl(String gatewayUrl) {
            this.gatewayUrl = gatewayUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Set<String> getGatewayAllowedHosts() {
            return gatewayAllowedHosts;
        }

        public void setGatewayAllowedHosts(Set<String> gatewayAllowedHosts) {
            this.gatewayAllowedHosts = gatewayAllowedHosts == null
                    ? new LinkedHashSet<>()
                    : new LinkedHashSet<>(gatewayAllowedHosts);
        }

        public Set<String> getSourceAllowedHosts() {
            return sourceAllowedHosts;
        }

        public void setSourceAllowedHosts(Set<String> sourceAllowedHosts) {
            this.sourceAllowedHosts = sourceAllowedHosts == null
                    ? new LinkedHashSet<>()
                    : new LinkedHashSet<>(sourceAllowedHosts);
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout == null || timeout.isNegative() || timeout.isZero()
                    ? Duration.ofSeconds(8)
                    : timeout;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = Math.max(1, Math.min(maxResults, 20));
        }

        public int getMaxResponseBytes() {
            return maxResponseBytes;
        }

        public void setMaxResponseBytes(int maxResponseBytes) {
            this.maxResponseBytes = Math.max(1_024, Math.min(maxResponseBytes, 5_000_000));
        }
    }
}
