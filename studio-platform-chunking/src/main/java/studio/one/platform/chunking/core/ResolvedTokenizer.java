package studio.one.platform.chunking.core;

import java.util.List;

/**
 * Tokenizer selected for a chunking or RAG operation.
 */
public record ResolvedTokenizer(
        TokenizerPort tokenizer,
        String provider,
        String encoding,
        String tokenizerModel,
        String selectionSource,
        String confidence,
        boolean fallbackUsed,
        List<String> warnings) {

    public ResolvedTokenizer {
        if (tokenizer == null) {
            throw new IllegalArgumentException("tokenizer must not be null");
        }
        provider = normalize(provider);
        encoding = normalize(encoding);
        tokenizerModel = normalize(tokenizerModel);
        selectionSource = normalize(selectionSource);
        confidence = normalize(confidence);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static ResolvedTokenizer of(
            TokenizerPort tokenizer,
            String selectionSource,
            String confidence,
            boolean fallbackUsed,
            List<String> warnings) {
        return new ResolvedTokenizer(
                tokenizer,
                tokenizer.provider(),
                tokenizer.encodingName(),
                null,
                selectionSource,
                confidence,
                fallbackUsed,
                warnings);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
