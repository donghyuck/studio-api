package studio.one.platform.ai.core.embedding;

/**
 * Logical input type for an embedding request.
 * <p>
 * The current provider adapters embed text. Non-plain-text document structures
 * such as tables, image captions, and OCR output are represented as text while
 * preserving their source input type in metadata.
 */
public enum EmbeddingInputType {
    TEXT,
    TABLE_TEXT,
    IMAGE_CAPTION,
    OCR_TEXT
}
