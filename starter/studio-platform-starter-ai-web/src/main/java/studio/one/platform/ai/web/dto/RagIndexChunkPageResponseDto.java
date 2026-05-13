package studio.one.platform.ai.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RagIndexChunkPageResponseDto {
    private final List<RagIndexChunkDto> items;
    private final int offset;
    private final int limit;
    private final int returned;
    private final boolean hasMore;

    @JsonCreator
    public RagIndexChunkPageResponseDto(
            @JsonProperty("items") List<RagIndexChunkDto> items,
            @JsonProperty("offset") int offset,
            @JsonProperty("limit") int limit,
            @JsonProperty("returned") int returned,
            @JsonProperty("hasMore") boolean hasMore) {
        this.items = items == null ? List.of() : List.copyOf(items);
        this.offset = Math.max(0, offset);
        this.limit = Math.max(0, limit);
        this.returned = Math.max(0, returned);
        this.hasMore = hasMore;
    }

    public List<RagIndexChunkDto> items() { return items; }
    public int offset() { return offset; }
    public int limit() { return limit; }
    public int returned() { return returned; }
    public boolean hasMore() { return hasMore; }

    public List<RagIndexChunkDto> getItems() { return items; }
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }
    public int getReturned() { return returned; }
    public boolean isHasMore() { return hasMore; }
}
