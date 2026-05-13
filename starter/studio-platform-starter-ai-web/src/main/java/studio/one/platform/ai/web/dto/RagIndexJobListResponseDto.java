package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class RagIndexJobListResponseDto {

    private final List<RagIndexJobDto> items;

    private final long total;

    private final int offset;

    private final int limit;

    @JsonCreator
    public RagIndexJobListResponseDto(
            @JsonProperty("items") List<RagIndexJobDto> items,
            @JsonProperty("total") long total,
            @JsonProperty("offset") int offset,
            @JsonProperty("limit") int limit
    ) {
        this.items = items;
        this.total = total;
        this.offset = offset;
        this.limit = limit;
    }

    public List<RagIndexJobDto> items() {
        return items;
    }

    public List<RagIndexJobDto> getItems() {
        return items;
    }

    public long total() {
        return total;
    }

    public long getTotal() {
        return total;
    }

    public int offset() {
        return offset;
    }

    public int getOffset() {
        return offset;
    }

    public int limit() {
        return limit;
    }

    public int getLimit() {
        return limit;
    }

}