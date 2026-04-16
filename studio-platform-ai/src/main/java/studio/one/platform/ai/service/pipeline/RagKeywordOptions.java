package studio.one.platform.ai.service.pipeline;

/**
 * Runtime options for RAG keyword metadata and query expansion.
 */
public record RagKeywordOptions(
        KeywordScope scope,
        int maxInputChars,
        boolean queryExpansionEnabled,
        int queryExpansionMaxKeywords) {

    public static final KeywordScope DEFAULT_SCOPE = KeywordScope.DOCUMENT;
    public static final int DEFAULT_MAX_INPUT_CHARS = 4_000;
    public static final boolean DEFAULT_QUERY_EXPANSION_ENABLED = true;
    public static final int DEFAULT_QUERY_EXPANSION_MAX_KEYWORDS = 10;

    public RagKeywordOptions {
        scope = scope == null ? DEFAULT_SCOPE : scope;
        if (maxInputChars < 1) {
            throw new IllegalArgumentException("maxInputChars must be greater than 0");
        }
        if (queryExpansionMaxKeywords < 1) {
            throw new IllegalArgumentException("queryExpansionMaxKeywords must be greater than 0");
        }
    }

    public static RagKeywordOptions defaults() {
        return new RagKeywordOptions(
                DEFAULT_SCOPE,
                DEFAULT_MAX_INPUT_CHARS,
                DEFAULT_QUERY_EXPANSION_ENABLED,
                DEFAULT_QUERY_EXPANSION_MAX_KEYWORDS);
    }

    public enum KeywordScope {
        DOCUMENT,
        CHUNK,
        BOTH;

        public static KeywordScope from(String value) {
            if (value == null || value.isBlank()) {
                return DEFAULT_SCOPE;
            }
            return KeywordScope.valueOf(value.trim().replace('-', '_').toUpperCase());
        }

        public boolean includesDocument() {
            return this == DOCUMENT || this == BOTH;
        }

        public boolean includesChunk() {
            return this == CHUNK || this == BOTH;
        }
    }
}
