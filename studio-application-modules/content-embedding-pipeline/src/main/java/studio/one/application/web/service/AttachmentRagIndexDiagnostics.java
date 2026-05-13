package studio.one.application.web.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Debug-safe diagnostics for attachment RAG indexing.
 */
public class AttachmentRagIndexDiagnostics {
    private final String path;
    private final boolean structured;
    private final String fallbackReason;
    private final int parsedBlockCount;
    private final int chunkCount;
    private final int vectorCount;

    public AttachmentRagIndexDiagnostics(
            String path,
            boolean structured,
            String fallbackReason,
            int parsedBlockCount,
            int chunkCount,
            int vectorCount) {
        this.path = path;
        this.structured = structured;
        this.fallbackReason = fallbackReason;
        this.parsedBlockCount = parsedBlockCount;
        this.chunkCount = chunkCount;
        this.vectorCount = vectorCount;
    }

    public static AttachmentRagIndexDiagnostics structured(int parsedBlockCount, int chunkCount, int vectorCount) {
        return new AttachmentRagIndexDiagnostics("structured", true, null,
                Math.max(0, parsedBlockCount), Math.max(0, chunkCount), Math.max(0, vectorCount));
    }

    public static AttachmentRagIndexDiagnostics structuredUnknown() {
        return new AttachmentRagIndexDiagnostics("structured", true, null, -1, -1, -1);
    }

    public static AttachmentRagIndexDiagnostics fallback(String reason) {
        return new AttachmentRagIndexDiagnostics("fallback", false, normalize(reason), 0, 0, 0);
    }

    public String path() {
        return path;
    }

    public boolean structured() {
        return structured;
    }

    public String fallbackReason() {
        return fallbackReason;
    }

    public int parsedBlockCount() {
        return parsedBlockCount;
    }

    public int chunkCount() {
        return chunkCount;
    }

    public int vectorCount() {
        return vectorCount;
    }

    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("path", path);
        metadata.put("structured", structured);
        put(metadata, "fallbackReason", fallbackReason);
        putCount(metadata, "parsedBlockCount", parsedBlockCount);
        putCount(metadata, "chunkCount", chunkCount);
        putCount(metadata, "vectorCount", vectorCount);
        return Map.copyOf(metadata);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void put(Map<String, Object> metadata, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String && ((String) value).isBlank()) {
            return;
        }
        metadata.put(key, value);
    }

    private static void putCount(Map<String, Object> metadata, String key, int value) {
        if (value >= 0) {
            metadata.put(key, value);
        }
    }
}
