package studio.one.platform.objecttype.model;

import java.util.Locale;

public enum ObjectTypeStatus {
    ACTIVE,
    DEPRECATED,
    DISABLED;

    public static ObjectTypeStatus from(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toUpperCase(Locale.ROOT);
        if (v.isEmpty()) {
            return null;
        }
        try {
            return ObjectTypeStatus.valueOf(v);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public String toStorage() {
        return name().toLowerCase(Locale.ROOT);
    }
}
