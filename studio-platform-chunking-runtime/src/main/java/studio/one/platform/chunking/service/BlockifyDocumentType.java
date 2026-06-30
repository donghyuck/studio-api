package studio.one.platform.chunking.service;

import java.util.Locale;

public enum BlockifyDocumentType {
    AUTO("auto"),
    POLICY("policy"),
    MANUAL("manual"),
    NARRATIVE("narrative"),
    TECHNICAL("technical"),
    TABLE_HEAVY("table-heavy"),
    GENERAL("general");

    private final String value;

    BlockifyDocumentType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static BlockifyDocumentType from(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (BlockifyDocumentType type : values()) {
            if (type.value.equals(normalized) || type.name().toLowerCase(Locale.ROOT).replace('_', '-').equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported blockify document type: " + value);
    }
}
