package studio.one.platform.chunking.core;

/**
 * Unit used to interpret chunk size and overlap limits.
 */
public enum ChunkUnit {
    CHARACTER("character"),
    TOKEN("token");

    private final String value;

    ChunkUnit(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ChunkUnit from(String value) {
        if (value == null || value.isBlank()) {
            return CHARACTER;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase();
        for (ChunkUnit unit : values()) {
            if (unit.name().equals(normalized) || unit.value.equalsIgnoreCase(value.trim())) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Unsupported chunk unit: " + value
                + ". Valid values are: CHARACTER, TOKEN");
    }
}
