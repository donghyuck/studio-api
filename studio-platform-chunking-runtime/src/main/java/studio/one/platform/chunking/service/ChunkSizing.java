package studio.one.platform.chunking.service;

import studio.one.platform.chunking.core.ChunkUnit;

final class ChunkSizing {

    private static final int AVERAGE_TOKEN_CHARS = 4;

    private ChunkSizing() {
    }

    static int effectiveMaxSize(int requestedMaxSize, int defaultMaxSize) {
        return requestedMaxSize <= 0 ? defaultMaxSize : requestedMaxSize;
    }

    static int effectiveOverlap(int requestedOverlap, int defaultOverlap, int maxSize) {
        int overlap = requestedOverlap < 0 ? defaultOverlap : requestedOverlap;
        return Math.min(overlap, maxSize - 1);
    }

    static int sizeOf(String text, ChunkUnit unit) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return unit == ChunkUnit.TOKEN ? estimateTokens(text) : text.length();
    }

    static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int compactLength = text.replaceAll("\\s+", " ").trim().length();
        return Math.max(1, (int) Math.ceil((double) compactLength / AVERAGE_TOKEN_CHARS));
    }
}
