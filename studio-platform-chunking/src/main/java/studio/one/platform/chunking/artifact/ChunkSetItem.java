package studio.one.platform.chunking.artifact;

import java.util.Map;

public record ChunkSetItem(
        int chunkIndex,
        String chunkId,
        String text,
        String contentHash,
        Map<String, Object> metadata) {

    public ChunkSetItem {
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        chunkId = normalizeRequired(chunkId, "chunkId");
        text = text == null ? "" : text;
        contentHash = normalizeRequired(contentHash, "contentHash");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
