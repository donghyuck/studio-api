package studio.one.platform.ai.service.chunk;

import studio.one.platform.ai.core.chunk.TextChunk;
import studio.one.platform.ai.core.chunk.TextChunker;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Simple text splitter that creates overlapping fixed-size chunks.
 */
public class OverlapTextChunker implements TextChunker {

    private final int chunkSize;
    private final int overlap;

    public OverlapTextChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap cannot be negative");
        }
        this.chunkSize = chunkSize;
        this.overlap = Math.min(overlap, chunkSize - 1);
    }

    @Override
    public List<TextChunk> chunk(String documentId, String text) {
        List<TextChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int start = 0;
        int length = text.length();
        int index = 0;
        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            String chunkText = text.substring(start, end);
            String chunkId = documentId + "-" + index + "-" + UUID.randomUUID();
            chunks.add(new TextChunk(chunkId, chunkText));
            if (end == length) {
                break;
            }
            start = end - overlap;
            index++;
        }
        return chunks;
    }
}
