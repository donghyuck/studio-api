package studio.one.platform.chunking.core;

/**
 * Logical chunk role used by RAG-oriented chunking.
 */
public enum ChunkType {
    PARENT("parent"),
    CHILD("child"),
    TABLE("table"),
    OCR("ocr"),
    IMAGE_CAPTION("image-caption"),
    SLIDE("slide");

    private final String value;

    ChunkType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ChunkType from(String value) {
        if (value == null || value.isBlank()) {
            return CHILD;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase();
        for (ChunkType type : values()) {
            if (type.name().equals(normalized) || type.value.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported chunk type: " + value);
    }
}
