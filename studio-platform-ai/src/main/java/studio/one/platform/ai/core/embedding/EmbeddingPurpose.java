package studio.one.platform.ai.core.embedding;

/**
 * Semantic purpose of an embedding request.
 *
 * <p>This is separate from {@link EmbeddingInputType}, which describes the
 * source content representation. Providers such as Google use the purpose to
 * distinguish document/index embeddings from query embeddings.</p>
 */
public enum EmbeddingPurpose {
    INDEX,
    QUERY,
    UNSPECIFIED
}
