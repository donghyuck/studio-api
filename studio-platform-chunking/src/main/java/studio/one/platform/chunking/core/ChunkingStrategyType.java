package studio.one.platform.chunking.core;

/**
 * Supported chunking strategy identifiers.
 */
public enum ChunkingStrategyType {
    FIXED_SIZE("fixed-size"),
    RECURSIVE("recursive"),
    STRUCTURE_BASED("structure-based"),
    SEMANTIC("semantic"),
    LLM_BASED("llm-based");

    private final String value;

    ChunkingStrategyType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ChunkingStrategyType from(String value) {
        if (value == null || value.isBlank()) {
            return RECURSIVE;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase();
        for (ChunkingStrategyType type : values()) {
            if (type.name().equals(normalized) || type.value.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported chunking strategy: " + value
                + ". Known values are: fixed-size, recursive, structure-based, semantic, llm-based. "
                + "Phase 1 starter support is limited to fixed-size and recursive.");
    }
}
