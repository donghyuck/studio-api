package studio.one.platform.ai.core.rag;

import java.util.Locale;

public record RagIndexJobPageRequest(
        int offset,
        int limit,
        Sort sort,
        Direction direction) {

    public RagIndexJobPageRequest {
        offset = Math.max(0, offset);
        if (limit <= 0) {
            limit = 50;
        }
        limit = Math.min(limit, 200);
        sort = sort == null ? Sort.CREATED_AT : sort;
        direction = direction == null ? Direction.DESC : direction;
    }

    public RagIndexJobPageRequest(int offset, int limit) {
        this(offset, limit, Sort.CREATED_AT, Direction.DESC);
    }

    public static RagIndexJobPageRequest defaults() {
        return new RagIndexJobPageRequest(0, 50);
    }

    public enum Sort {
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

        public static Sort from(String value) {
            if (value == null || value.isBlank()) {
                return CREATED_AT;
            }
            String normalized = normalize(value);
            for (Sort sort : values()) {
                if (normalize(sort.name()).equals(normalized)) {
                    return sort;
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
        return value.replace("-", "")
                .replace("_", "")
                .toUpperCase(Locale.ROOT);
    }
}
