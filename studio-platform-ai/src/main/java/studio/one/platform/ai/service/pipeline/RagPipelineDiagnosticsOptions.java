package studio.one.platform.ai.service.pipeline;

/**
 * Runtime options for RAG retrieval diagnostics.
 */
public record RagPipelineDiagnosticsOptions(
        boolean enabled,
        boolean logResults,
        int maxSnippetChars) {

    public static final boolean DEFAULT_ENABLED = false;
    public static final boolean DEFAULT_LOG_RESULTS = false;
    public static final int DEFAULT_MAX_SNIPPET_CHARS = 120;

    public RagPipelineDiagnosticsOptions {
        if (maxSnippetChars < 0) {
            throw new IllegalArgumentException("maxSnippetChars must be greater than or equal to 0");
        }
    }

    public static RagPipelineDiagnosticsOptions defaults() {
        return new RagPipelineDiagnosticsOptions(
                DEFAULT_ENABLED,
                DEFAULT_LOG_RESULTS,
                DEFAULT_MAX_SNIPPET_CHARS);
    }
}
