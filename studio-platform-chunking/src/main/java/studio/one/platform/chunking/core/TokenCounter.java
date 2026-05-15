package studio.one.platform.chunking.core;

/**
 * Minimal tokenizer contract for metadata and budget calculations.
 */
public interface TokenCounter {

    String provider();

    String encodingName();

    int countTokens(String text);
}
