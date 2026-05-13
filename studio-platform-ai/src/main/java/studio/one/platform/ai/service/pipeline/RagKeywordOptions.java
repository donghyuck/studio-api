package studio.one.platform.ai.service.pipeline;

/**
 * Runtime options for RAG keyword metadata and query expansion.
 */
public final class RagKeywordOptions {

    private final KeywordScope scope;
    private final int maxInputChars;
    private final boolean queryExpansionEnabled;
    private final int queryExpansionMaxKeywords;

    public RagKeywordOptions(
            KeywordScope scope,
            int maxInputChars,
            boolean queryExpansionEnabled,
            int queryExpansionMaxKeywords
    ) {
                scope = scope == null ? DEFAULT_SCOPE : scope;
                if (maxInputChars < 1) {
                    throw new IllegalArgumentException("maxInputChars must be greater than 0");
                }
                if (queryExpansionMaxKeywords < 1) {
                    throw new IllegalArgumentException("queryExpansionMaxKeywords must be greater than 0");
                }
        
        this.scope = scope;
        this.maxInputChars = maxInputChars;
        this.queryExpansionEnabled = queryExpansionEnabled;
        this.queryExpansionMaxKeywords = queryExpansionMaxKeywords;
    }

    public KeywordScope scope() {
        return scope;
    }

    public int maxInputChars() {
        return maxInputChars;
    }

    public boolean queryExpansionEnabled() {
        return queryExpansionEnabled;
    }

    public int queryExpansionMaxKeywords() {
        return queryExpansionMaxKeywords;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RagKeywordOptions)) {
            return false;
        }
        RagKeywordOptions that = (RagKeywordOptions) o;
        return java.util.Objects.equals(scope, that.scope)
                && maxInputChars == that.maxInputChars
                && queryExpansionEnabled == that.queryExpansionEnabled
                && queryExpansionMaxKeywords == that.queryExpansionMaxKeywords;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(scope, maxInputChars, queryExpansionEnabled, queryExpansionMaxKeywords);
    }

    @Override
    public String toString() {
        return "RagKeywordOptions[" +
                "scope=" + scope + ", " +
                "maxInputChars=" + maxInputChars + ", " +
                "queryExpansionEnabled=" + queryExpansionEnabled + ", " +
                "queryExpansionMaxKeywords=" + queryExpansionMaxKeywords +
                "]";
    }

    public static final KeywordScope DEFAULT_SCOPE = KeywordScope.DOCUMENT;
    public static final int DEFAULT_MAX_INPUT_CHARS = 4_000;
    public static final boolean DEFAULT_QUERY_EXPANSION_ENABLED = true;
    public static final int DEFAULT_QUERY_EXPANSION_MAX_KEYWORDS = 10;

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
            try {
                return KeywordScope.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid keyword scope '" + value
                        + "'. Valid values are: DOCUMENT, CHUNK, BOTH", ex);
            }
        }

        public boolean includesDocument() {
            return this == DOCUMENT || this == BOTH;
        }

        public boolean includesChunk() {
            return this == CHUNK || this == BOTH;
        }
    }
}
