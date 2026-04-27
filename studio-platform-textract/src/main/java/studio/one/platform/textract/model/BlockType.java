package studio.one.platform.textract.model;

/**
 * Logical block types used by structured file parsing.
 */
public enum BlockType {
    TITLE,
    DOCUMENT,
    PAGE,
    HEADER,
    FOOTER,
    PARAGRAPH,
    HEADING,
    LIST_ITEM,
    TABLE,
    TABLE_ROW,
    TABLE_CELL,
    IMAGE,
    IMAGE_CAPTION,
    FOOTNOTE,
    OCR_TEXT,
    METADATA,
    UNKNOWN
}
