package studio.one.platform.chunking.core;

/**
 * Parser-neutral logical block type used by structure-aware chunking.
 */
public enum NormalizedBlockType {
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
    UNKNOWN;

    public static NormalizedBlockType from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase();
        for (NormalizedBlockType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
