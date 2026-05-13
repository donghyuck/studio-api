package studio.one.platform.ai.service.pipeline;

/**
 * Runtime options for RAG retrieval diagnostics.
 */
public final class RagPipelineDiagnosticsOptions {

    private final boolean enabled;
    private final boolean logResults;
    private final int maxSnippetChars;

    public RagPipelineDiagnosticsOptions(
            boolean enabled,
            boolean logResults,
            int maxSnippetChars
    ) {
                if (maxSnippetChars < 0) {
                    throw new IllegalArgumentException("maxSnippetChars must be greater than or equal to 0");
                }
        
        this.enabled = enabled;
        this.logResults = logResults;
        this.maxSnippetChars = maxSnippetChars;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean logResults() {
        return logResults;
    }

    public int maxSnippetChars() {
        return maxSnippetChars;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RagPipelineDiagnosticsOptions)) {
            return false;
        }
        RagPipelineDiagnosticsOptions that = (RagPipelineDiagnosticsOptions) o;
        return enabled == that.enabled
                && logResults == that.logResults
                && maxSnippetChars == that.maxSnippetChars;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(enabled, logResults, maxSnippetChars);
    }

    @Override
    public String toString() {
        return "RagPipelineDiagnosticsOptions[" +
                "enabled=" + enabled + ", " +
                "logResults=" + logResults + ", " +
                "maxSnippetChars=" + maxSnippetChars +
                "]";
    }

    public static final boolean DEFAULT_ENABLED = false;
    public static final boolean DEFAULT_LOG_RESULTS = false;
    public static final int DEFAULT_MAX_SNIPPET_CHARS = 120;

    public static RagPipelineDiagnosticsOptions defaults() {
        return new RagPipelineDiagnosticsOptions(
                DEFAULT_ENABLED,
                DEFAULT_LOG_RESULTS,
                DEFAULT_MAX_SNIPPET_CHARS);
    }
}
