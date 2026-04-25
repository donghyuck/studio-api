package studio.one.platform.chunking.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;

final class ChunkContextExpansionSupport {

    private ChunkContextExpansionSupport() {
    }

    static List<Chunk> order(List<Chunk> chunks) {
        return chunks.stream()
                .filter(chunk -> chunk != null && chunk.content() != null && !chunk.content().isBlank())
                .distinct()
                .sorted(Comparator.comparingInt(chunk -> chunk.metadata().order()))
                .toList();
    }

    static List<Chunk> sameParent(String parentChunkId, List<Chunk> chunks) {
        if (parentChunkId == null || parentChunkId.isBlank()) {
            return List.of();
        }
        return order(chunks.stream()
                .filter(chunk -> parentChunkId.equals(chunk.metadata().parentChunkId()))
                .toList());
    }

    static List<Chunk> sameSection(String section, List<Chunk> chunks) {
        if (section == null || section.isBlank()) {
            return List.of();
        }
        return order(chunks.stream()
                .filter(chunk -> section.equals(chunk.metadata().section()))
                .toList());
    }

    static ChunkType chunkType(Chunk chunk) {
        ChunkType type = chunk.metadata().chunkType();
        if (type != null) {
            return type;
        }
        Object value = chunk.metadata().toMap().get(ChunkMetadata.KEY_CHUNK_TYPE);
        return value instanceof String stringValue ? ChunkType.from(stringValue) : ChunkType.CHILD;
    }

    static String parentContent(Chunk chunk) {
        Object value = chunk.metadata().toMap().get(ChunkMetadata.KEY_PARENT_CHUNK_CONTENT);
        return value instanceof String stringValue ? stringValue.trim() : "";
    }

    static Map<String, Object> metadata(String key, Object value) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (key != null && !key.isBlank() && value != null) {
            if (!(value instanceof String stringValue) || !stringValue.isBlank()) {
                metadata.put(key, value);
            }
        }
        return metadata;
    }
}
