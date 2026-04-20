package studio.one.platform.textract.model;

/**
 * Logical block types used by structured file parsing.
 */
public enum BlockType {
    DOCUMENT,
    PAGE,
    PARAGRAPH,
    HEADING,
    LIST_ITEM,
    TABLE,
    TABLE_ROW,
    TABLE_CELL,
    IMAGE,
    OCR_TEXT,
    METADATA,
    UNKNOWN
}
