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
        Map<String, Chunk> unique = new LinkedHashMap<>();
        chunks.stream()
                .filter(ChunkContextExpansionSupport::hasContent)
                .forEach(chunk -> unique.putIfAbsent(chunk.id(), chunk));
        return unique.values().stream()
                .sorted(Comparator.comparingInt(chunk -> chunk.metadata().order()))
                .toList();
    }

    static List<Chunk> withSeed(Chunk seed, List<Chunk> chunks) {
        Map<String, Chunk> unique = new LinkedHashMap<>();
        if (chunks != null) {
            chunks.stream()
                    .filter(ChunkContextExpansionSupport::hasContent)
                    .filter(chunk -> !chunk.id().equals(seed.id()))
                    .forEach(chunk -> unique.putIfAbsent(chunk.id(), chunk));
        }
        unique.put(seed.id(), seed);
        return List.copyOf(unique.values());
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
        return chunk.metadata().chunkType();
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

    private static boolean hasContent(Chunk chunk) {
        return chunk != null && chunk.content() != null && !chunk.content().isBlank();
    }
}
