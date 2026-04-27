package studio.one.platform.ai.core.rag;

import java.util.Locale;

public record RagIndexJobSort(Field field, Direction direction) {

    public RagIndexJobSort {
        field = field == null ? Field.CREATED_AT : field;
        direction = direction == null ? Direction.DESC : direction;
    }

    public static RagIndexJobSort defaults() {
        return new RagIndexJobSort(Field.CREATED_AT, Direction.DESC);
    }

    public enum Field {
        CREATED_AT,
        STARTED_AT,
        FINISHED_AT,
        STATUS,
        CURRENT_STEP,
        OBJECT_TYPE,
        OBJECT_ID,
        DOCUMENT_ID,
        SOURCE_TYPE,
        DURATION_MS;

        public static Field from(String value) {
            if (value == null || value.isBlank()) {
                return CREATED_AT;
            }
            String normalized = normalize(value);
            for (Field field : values()) {
                if (normalize(field.name()).equals(normalized)) {
                    return field;
                }
            }
            return CREATED_AT;
        }
    }

    public enum Direction {
        ASC,
        DESC;

        public static Direction from(String value) {
            if (value == null || value.isBlank()) {
                return DESC;
            }
            try {
                return Direction.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return DESC;
            }
        }
    }

    private static String normalize(String value) {
        return value.trim()
                .replace("-", "")
                .replace("_", "")
                .toUpperCase(Locale.ROOT);
    }
}
