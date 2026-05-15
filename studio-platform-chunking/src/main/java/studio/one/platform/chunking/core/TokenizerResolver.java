package studio.one.platform.chunking.core;

import java.util.Map;

/**
 * Resolves a tokenizer from operation metadata such as embedding provider/model
 * and explicit tokenizer settings.
 */
public interface TokenizerResolver {

    ResolvedTokenizer resolve(Map<String, Object> metadata);
}
