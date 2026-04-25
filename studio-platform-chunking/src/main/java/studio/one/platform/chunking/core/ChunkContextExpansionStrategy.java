package studio.one.platform.chunking.core;

/**
 * Provider-neutral context expansion strategy identifier.
 */
public enum ChunkContextExpansionStrategy {
    PARENT_CHILD("parent-child"),
    WINDOW("window"),
    HEADING("heading"),
    TABLE("table"),
    CUSTOM("custom"),
    UNKNOWN("unknown");

    private final String value;

    ChunkContextExpansionStrategy(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ChunkContextExpansionStrategy from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase();
        for (ChunkContextExpansionStrategy strategy : values()) {
            if (strategy.name().equals(normalized) || strategy.value.equalsIgnoreCase(value.trim())) {
                return strategy;
            }
        }
        return UNKNOWN;
    }
}
