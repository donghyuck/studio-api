package studio.one.platform.ai.web.dto;

import java.util.List;

public record RagIndexChunkPageResponseDto(
        List<RagIndexChunkDto> items,
        int offset,
        int limit,
        int returned,
        boolean hasMore) {

    public RagIndexChunkPageResponseDto {
        items = items == null ? List.of() : List.copyOf(items);
        offset = Math.max(0, offset);
        limit = Math.max(0, limit);
        returned = Math.max(0, returned);
    }
}
